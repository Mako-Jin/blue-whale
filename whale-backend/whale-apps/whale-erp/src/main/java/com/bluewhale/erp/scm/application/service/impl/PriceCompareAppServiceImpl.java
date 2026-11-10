package com.bluewhale.erp.scm.application.service.impl;

import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.scm.application.service.PriceCompareAppService;
import com.bluewhale.erp.scm.application.view.PriceTrendView;
import com.bluewhale.erp.scm.application.view.SupplierPriceCompareView;
import com.bluewhale.erp.scm.domain.model.PriceList;
import com.bluewhale.erp.scm.domain.model.PriceListQuery;
import com.bluewhale.erp.scm.domain.model.PriceType;
import com.bluewhale.erp.scm.domain.model.PurchasePriceRecord;
import com.bluewhale.erp.scm.domain.repository.PriceListRepository;
import com.bluewhale.erp.scm.domain.repository.PurchaseOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 采购比价与价格趋势应用服务实现。
 *
 * <p>比价以价目表「供应商采购价」为当前价、采购订单为历史成交价；
 * 趋势按有效采购订单（审核后）按月做数量加权平均，草稿与已取消不计入。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class PriceCompareAppServiceImpl implements PriceCompareAppService {

    /** 默认统计月数 */
    private static final int DEFAULT_MONTHS = 6;

    /** 最大统计月数 */
    private static final int MAX_MONTHS = 24;

    private static final int PRICE_SCALE = 4;

    private final PriceListRepository priceListRepository;

    private final PurchaseOrderRepository purchaseOrderRepository;

    @Override
    public List<SupplierPriceCompareView> compare(Long itemId, LocalDate date) {
        requireItem(itemId);
        LocalDate effectiveDate = date == null ? LocalDate.now() : date;
        Map<Long, SupplierPriceCompareView> views = new LinkedHashMap<>();
        for (PriceList priceList : currentPrices(itemId, effectiveDate)) {
            SupplierPriceCompareView view = views.computeIfAbsent(priceList.getPartnerId(),
                    key -> newView(key, priceList.getPartnerName()));
            view.setPrice(priceList.getPrice());
            view.setValidFrom(priceList.getValidFrom());
            view.setValidTo(priceList.getValidTo());
        }
        for (PurchasePriceRecord record : lastPurchases(itemId).values()) {
            SupplierPriceCompareView view = views.computeIfAbsent(record.getSupplierId(),
                    key -> newView(key, record.getSupplierName()));
            view.setSupplierName(record.getSupplierName());
            view.setLastPurchasePrice(record.getUnitPrice());
            view.setLastPurchaseDate(record.getDocDate());
            if (view.getCurrency() == null) {
                view.setCurrency(record.getCurrency());
            }
        }
        BigDecimal lowest = views.values().stream().map(SupplierPriceCompareView::getPrice)
                .filter(Objects::nonNull).min(BigDecimal::compareTo).orElse(null);
        for (SupplierPriceCompareView view : views.values()) {
            BigDecimal price = view.getPrice();
            view.setLowest(price != null && lowest != null && price.compareTo(lowest) == 0);
            view.setGap(price == null || lowest == null ? null : price.subtract(lowest));
        }
        List<SupplierPriceCompareView> result = new ArrayList<>(views.values());
        result.sort(Comparator.comparing(SupplierPriceCompareView::getPrice,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return result;
    }

    @Override
    public List<PriceTrendView> trend(Long itemId, Integer months) {
        requireItem(itemId);
        int span = months == null ? DEFAULT_MONTHS : months;
        if (span < 1 || span > MAX_MONTHS) {
            throw ErpErrorCode.exception(ErpErrorCode.PRICE_INVALID, "统计月数需在 1–24 之间");
        }
        LocalDate fromDate = LocalDate.now().withDayOfMonth(1).minusMonths(span - 1L);
        Map<YearMonth, List<PurchasePriceRecord>> grouped = new LinkedHashMap<>();
        for (PurchasePriceRecord record : purchaseOrderRepository.listPurchasePrices(itemId, fromDate)) {
            if (!record.effective() || record.getDocDate() == null) {
                continue;
            }
            grouped.computeIfAbsent(YearMonth.from(record.getDocDate()), key -> new ArrayList<>()).add(record);
        }
        List<PriceTrendView> result = new ArrayList<>();
        for (Map.Entry<YearMonth, List<PurchasePriceRecord>> entry : grouped.entrySet()) {
            List<PurchasePriceRecord> records = entry.getValue();
            PriceTrendView view = new PriceTrendView();
            view.setMonth(entry.getKey().toString());
            view.setAvgPrice(weightedAverage(records));
            view.setMinPrice(records.stream().map(PurchasePriceRecord::getUnitPrice)
                    .min(BigDecimal::compareTo).orElse(null));
            view.setMaxPrice(records.stream().map(PurchasePriceRecord::getUnitPrice)
                    .max(BigDecimal::compareTo).orElse(null));
            view.setOrderCount(records.size());
            result.add(view);
        }
        result.sort(Comparator.comparing(PriceTrendView::getMonth));
        return result;
    }

    /**
     * 查询指定日期有效的供应商采购价。
     *
     * @param itemId 物料主键
     * @param date 取价日期
     * @return 价目列表
     */
    private List<PriceList> currentPrices(Long itemId, LocalDate date) {
        PriceListQuery query = new PriceListQuery();
        query.setPriceType(PriceType.PURCHASE);
        query.setItemId(itemId);
        return priceListRepository.list(query).stream()
                .filter(priceList -> priceList.matches(date))
                .toList();
    }

    /**
     * 每个供应商最近一次有效成交。
     *
     * @param itemId 物料主键
     * @return 供应商主键 → 最近成交
     */
    private Map<Long, PurchasePriceRecord> lastPurchases(Long itemId) {
        Map<Long, PurchasePriceRecord> latest = new LinkedHashMap<>();
        for (PurchasePriceRecord record : purchaseOrderRepository.listPurchasePrices(itemId, null)) {
            if (!record.effective() || record.getSupplierId() == null) {
                continue;
            }
            latest.merge(record.getSupplierId(), record,
                    (existing, candidate) -> existing.getDocDate().isAfter(candidate.getDocDate())
                            ? existing : candidate);
        }
        return latest;
    }

    /**
     * 按数量加权平均；数量缺失或合计为 0 时退化为简单平均。
     *
     * @param records 价格记录
     * @return 平均单价
     */
    private BigDecimal weightedAverage(List<PurchasePriceRecord> records) {
        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal quantity = BigDecimal.ZERO;
        for (PurchasePriceRecord record : records) {
            BigDecimal qty = record.getQuantity() == null ? BigDecimal.ZERO : record.getQuantity();
            if (record.getUnitPrice() != null) {
                amount = amount.add(record.getUnitPrice().multiply(qty));
            }
            quantity = quantity.add(qty);
        }
        if (quantity.signum() != 0) {
            return amount.divide(quantity, PRICE_SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal sum = records.stream().map(PurchasePriceRecord::getUnitPrice)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        return records.isEmpty() ? BigDecimal.ZERO
                : sum.divide(BigDecimal.valueOf(records.size()), PRICE_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 校验物料主键。
     *
     * @param itemId 物料主键
     */
    private void requireItem(Long itemId) {
        if (itemId == null) {
            throw ErpErrorCode.exception(ErpErrorCode.PRICE_INVALID, "请指定物料");
        }
    }

    /**
     * 构造比价视图。
     *
     * @param supplierId 供应商主键
     * @param supplierName 供应商名称
     * @return 视图
     */
    private SupplierPriceCompareView newView(Long supplierId, String supplierName) {
        SupplierPriceCompareView view = new SupplierPriceCompareView();
        view.setSupplierId(supplierId);
        view.setSupplierName(supplierName);
        view.setLowest(false);
        return view;
    }
}