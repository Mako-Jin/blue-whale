package com.bluewhale.erp.scm.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.masterdata.domain.model.MasterDataStatus;
import com.bluewhale.erp.masterdata.domain.model.Warehouse;
import com.bluewhale.erp.masterdata.domain.repository.WarehouseRepository;
import com.bluewhale.erp.scm.application.command.SafetyStockCreateCommand;
import com.bluewhale.erp.scm.application.command.SafetyStockUpdateCommand;
import com.bluewhale.erp.scm.application.service.SafetyStockAppService;
import com.bluewhale.erp.scm.application.view.IdleStockView;
import com.bluewhale.erp.scm.application.view.SafetyStockView;
import com.bluewhale.erp.scm.application.view.ShortageView;
import com.bluewhale.erp.scm.domain.model.SafetyStock;
import com.bluewhale.erp.scm.domain.model.SafetyStockQuery;
import com.bluewhale.erp.scm.domain.model.ScmQuery;
import com.bluewhale.erp.scm.domain.model.StockBalance;
import com.bluewhale.erp.scm.domain.port.ItemCatalog;
import com.bluewhale.erp.scm.domain.port.ItemSnapshot;
import com.bluewhale.erp.scm.domain.repository.SafetyStockRepository;
import com.bluewhale.erp.scm.domain.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 安全库存应用服务实现。
 *
 * <p>缺料预警与呆滞分析均为查询计算：前者按安全库存配置与库存余额逐条比较；
 * 后者按余额数量大于 0 且最近出库时间早于阈值判定，不落快照表。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class SafetyStockAppServiceImpl implements SafetyStockAppService {

    /** 默认呆滞天数 */
    private static final int DEFAULT_IDLE_DAYS = 90;

    private final SafetyStockRepository safetyStockRepository;

    private final StockRepository stockRepository;

    private final WarehouseRepository warehouseRepository;

    private final ItemCatalog itemCatalog;

    @Override
    public PageResult<SafetyStockView> page(SafetyStockQuery query) {
        List<SafetyStockView> records = safetyStockRepository.page(query).stream()
                .map(SafetyStockView::of).toList();
        return PageResult.of(records, safetyStockRepository.count(query), query.getCurrent(), query.getSize());
    }

    @Override
    public SafetyStockView detail(Long id) {
        return SafetyStockView.of(requireSafetyStock(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SafetyStockView create(SafetyStockCreateCommand command) {
        Warehouse warehouse = requireWarehouse(command.getWarehouseId());
        ItemSnapshot item = requireItem(command.getItemCode());
        if (safetyStockRepository.find(warehouse.getId(), item.getId()).isPresent()) {
            throw ErpErrorCode.exception(ErpErrorCode.SAFETY_STOCK_CONFLICT);
        }
        SafetyStock safetyStock = new SafetyStock();
        safetyStock.setWarehouseId(warehouse.getId());
        safetyStock.setItemId(item.getId());
        safetyStock.setItemCode(item.getCode());
        safetyStock.setItemName(item.getName());
        safetyStock.setSafetyQty(command.getSafetyQty());
        safetyStock.setMaxQty(command.getMaxQty());
        safetyStock.setRemark(command.getRemark());
        safetyStock.validate();
        safetyStockRepository.insert(safetyStock);
        return SafetyStockView.of(safetyStock);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SafetyStockView update(Long id, SafetyStockUpdateCommand command) {
        SafetyStock safetyStock = requireSafetyStock(id);
        safetyStock.setSafetyQty(command.getSafetyQty());
        safetyStock.setMaxQty(command.getMaxQty());
        safetyStock.setRemark(command.getRemark());
        safetyStock.validate();
        safetyStockRepository.update(safetyStock);
        return SafetyStockView.of(safetyStock);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireSafetyStock(id);
        safetyStockRepository.deleteById(id);
    }

    @Override
    public PageResult<ShortageView> shortage(SafetyStockQuery query) {
        List<ShortageView> shortages = new ArrayList<>();
        for (SafetyStock safetyStock : safetyStockRepository.list(query)) {
            BigDecimal onHand = stockRepository.findBalance(safetyStock.getItemId(), safetyStock.getWarehouseId())
                    .map(StockBalance::getQuantity)
                    .orElse(BigDecimal.ZERO);
            BigDecimal shortageQty = safetyStock.getSafetyQty().subtract(onHand);
            if (shortageQty.signum() > 0) {
                ShortageView view = new ShortageView();
                view.setWarehouseId(safetyStock.getWarehouseId());
                view.setItemId(safetyStock.getItemId());
                view.setItemCode(safetyStock.getItemCode());
                view.setItemName(safetyStock.getItemName());
                view.setOnHandQty(onHand);
                view.setSafetyQty(safetyStock.getSafetyQty());
                view.setShortageQty(shortageQty);
                shortages.add(view);
            }
        }
        shortages.sort(Comparator.comparing(ShortageView::getShortageQty).reversed());
        return PageResult.of(slice(shortages, query.getCurrent(), query.getSize()), shortages.size(),
                query.getCurrent(), query.getSize());
    }

    @Override
    public PageResult<IdleStockView> idle(ScmQuery query, Integer idleDays) {
        int threshold = idleDays == null ? DEFAULT_IDLE_DAYS : idleDays;
        if (threshold < 1) {
            throw ErpErrorCode.exception(ErpErrorCode.SAFETY_STOCK_INVALID, "呆滞天数必须大于 0");
        }
        LocalDate today = LocalDate.now();
        List<IdleStockView> idles = new ArrayList<>();
        for (StockBalance balance : stockRepository.listBalances(query)) {
            if (balance.getQuantity() == null || balance.getQuantity().signum() <= 0) {
                continue;
            }
            LocalDateTime lastOutboundAt = stockRepository
                    .lastOutboundAt(balance.getItemId(), balance.getWarehouseId()).orElse(null);
            Integer days = lastOutboundAt == null ? null
                    : (int) ChronoUnit.DAYS.between(lastOutboundAt.toLocalDate(), today);
            if (days != null && days < threshold) {
                continue;
            }
            IdleStockView view = new IdleStockView();
            view.setWarehouseId(balance.getWarehouseId());
            view.setItemId(balance.getItemId());
            view.setItemCode(balance.getItemCode());
            view.setItemName(balance.getItemName());
            view.setQuantity(balance.getQuantity());
            view.setAmount(balance.getAmount());
            view.setLastOutboundAt(lastOutboundAt);
            view.setIdleDays(days);
            idles.add(view);
        }
        idles.sort(Comparator
                .comparing(IdleStockView::getIdleDays, Comparator.nullsFirst(Comparator.<Integer>reverseOrder()))
                .thenComparing(IdleStockView::getAmount, Comparator.nullsLast(Comparator.reverseOrder())));
        return PageResult.of(slice(idles, query.getCurrent(), query.getSize()), idles.size(),
                query.getCurrent(), query.getSize());
    }

    /**
     * 按主键查询安全库存，不存在抛业务异常。
     *
     * @param id 主键
     * @return 安全库存
     */
    private SafetyStock requireSafetyStock(Long id) {
        return safetyStockRepository.findById(id)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.SAFETY_STOCK_NOT_FOUND));
    }

    /**
     * 校验仓库存在且启用。
     *
     * @param warehouseId 仓库主键
     * @return 仓库
     */
    private Warehouse requireWarehouse(Long warehouseId) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.WAREHOUSE_NOT_FOUND));
        if (!MasterDataStatus.ENABLED.equals(warehouse.getStatus())) {
            throw ErpErrorCode.exception(ErpErrorCode.WAREHOUSE_NOT_FOUND, "仓库已停用");
        }
        return warehouse;
    }

    /**
     * 校验物料存在且启用。
     *
     * @param itemCode 物料编码
     * @return 物料快照
     */
    private ItemSnapshot requireItem(String itemCode) {
        ItemSnapshot item = itemCatalog.findByCode(itemCode)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.ITEM_INVALID,
                        "物料 " + itemCode + " 不存在"));
        if (!MasterDataStatus.ENABLED.equals(item.getStatus())) {
            throw ErpErrorCode.exception(ErpErrorCode.ITEM_INVALID, "物料 " + item.getCode() + " 已停用");
        }
        return item;
    }

    /**
     * 按分页参数切片。
     *
     * @param records 全量记录
     * @param current 当前页
     * @param size 每页条数
     * @param <T> 记录类型
     * @return 当前页记录
     */
    private static <T> List<T> slice(List<T> records, long current, long size) {
        long from = Math.max((current - 1) * size, 0);
        if (from >= records.size()) {
            return List.of();
        }
        int to = (int) Math.min(from + size, records.size());
        return records.subList((int) from, to);
    }
}
