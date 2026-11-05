package com.bluewhale.erp.scm.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.masterdata.domain.model.Customer;
import com.bluewhale.erp.masterdata.domain.model.MasterDataStatus;
import com.bluewhale.erp.masterdata.domain.repository.CustomerRepository;
import com.bluewhale.erp.scm.application.command.PriceListCreateCommand;
import com.bluewhale.erp.scm.application.command.PriceListUpdateCommand;
import com.bluewhale.erp.scm.application.service.PriceListAppService;
import com.bluewhale.erp.scm.application.view.PriceListView;
import com.bluewhale.erp.scm.domain.model.PriceList;
import com.bluewhale.erp.scm.domain.model.PriceListQuery;
import com.bluewhale.erp.scm.domain.model.PriceType;
import com.bluewhale.erp.scm.domain.model.ScmStatus;
import com.bluewhale.erp.scm.domain.port.ItemCatalog;
import com.bluewhale.erp.scm.domain.port.ItemSnapshot;
import com.bluewhale.erp.scm.domain.port.SupplierCatalog;
import com.bluewhale.erp.scm.domain.port.SupplierSnapshot;
import com.bluewhale.erp.scm.domain.repository.PriceListRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

/**
 * 价目表应用服务实现。
 *
 * <p>价目以「价格类型 + 往来方 + 物料」唯一；新建时快照往来方与物料名称，
 * 订单/报价建单可按单据日期兜底取价。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class PriceListAppServiceImpl implements PriceListAppService {

    /** 默认币种 */
    private static final String DEFAULT_CURRENCY = "CNY";

    private final PriceListRepository priceListRepository;

    private final CustomerRepository customerRepository;

    private final SupplierCatalog supplierCatalog;

    private final ItemCatalog itemCatalog;

    @Override
    public PageResult<PriceListView> page(PriceListQuery query) {
        List<PriceListView> records = priceListRepository.page(query).stream()
                .map(PriceListView::of).toList();
        return PageResult.of(records, priceListRepository.count(query), query.getCurrent(), query.getSize());
    }

    @Override
    public PriceListView detail(Long id) {
        return PriceListView.of(requirePriceList(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PriceListView create(PriceListCreateCommand command) {
        PriceList priceList = new PriceList();
        priceList.setPriceType(command.getPriceType());
        fillPartner(priceList, command.getPriceType(), command.getPartnerId());
        fillItem(priceList, command.getItemCode(), command.getUom());
        priceList.setPrice(command.getPrice());
        priceList.setCurrency(StringUtils.hasText(command.getCurrency())
                ? command.getCurrency() : DEFAULT_CURRENCY);
        priceList.setValidFrom(command.getValidFrom());
        priceList.setValidTo(command.getValidTo());
        priceList.setStatus(ScmStatus.ENABLED);
        priceList.setRemark(command.getRemark());
        priceList.validate();
        if (priceListRepository.find(priceList.getPriceType(), priceList.getPartnerId(),
                priceList.getItemId()).isPresent()) {
            throw ErpErrorCode.exception(ErpErrorCode.PRICE_LIST_CONFLICT);
        }
        priceListRepository.insert(priceList);
        return PriceListView.of(priceList);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PriceListView update(Long id, PriceListUpdateCommand command) {
        PriceList priceList = requirePriceList(id);
        priceList.setPrice(command.getPrice());
        priceList.setValidFrom(command.getValidFrom());
        priceList.setValidTo(command.getValidTo());
        priceList.setRemark(command.getRemark());
        if (StringUtils.hasText(command.getStatus())) {
            priceList.setStatus(command.getStatus());
        }
        priceList.validate();
        priceListRepository.update(priceList);
        return PriceListView.of(priceList);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PriceListView toggle(Long id) {
        PriceList priceList = requirePriceList(id);
        priceList.toggle();
        priceListRepository.update(priceList);
        return PriceListView.of(priceList);
    }

    @Override
    public PriceListView resolve(PriceType priceType, Long partnerId, Long itemId, LocalDate date) {
        LocalDate effectiveDate = date == null ? LocalDate.now() : date;
        PriceList priceList = priceListRepository.find(priceType, partnerId, itemId)
                .filter(candidate -> candidate.matches(effectiveDate))
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.PRICE_LIST_NOT_FOUND));
        return PriceListView.of(priceList);
    }

    /**
     * 按主键查询价目，不存在抛业务异常。
     *
     * @param id 主键
     * @return 价目
     */
    private PriceList requirePriceList(Long id) {
        return priceListRepository.findById(id)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.PRICE_LIST_NOT_FOUND));
    }

    /**
     * 校验往来方并快照名称。
     *
     * @param priceList 价目
     * @param priceType 价格类型
     * @param partnerId 往来方主键
     */
    private void fillPartner(PriceList priceList, PriceType priceType, Long partnerId) {
        if (PriceType.SALES == priceType) {
            Customer customer = requireCustomer(partnerId);
            priceList.setPartnerId(customer.getId());
            priceList.setPartnerName(customer.getName());
            return;
        }
        SupplierSnapshot supplier = requireSupplier(partnerId);
        priceList.setPartnerId(supplier.getId());
        priceList.setPartnerName(supplier.getName());
    }

    /**
     * 校验物料并快照编码与名称。
     *
     * @param priceList 价目
     * @param itemCode 物料编码
     * @param uom 计量单位
     */
    private void fillItem(PriceList priceList, String itemCode, String uom) {
        ItemSnapshot item = itemCatalog.findByCode(itemCode)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.ITEM_INVALID,
                        "物料 " + itemCode + " 不存在"));
        if (!MasterDataStatus.ENABLED.equals(item.getStatus())) {
            throw ErpErrorCode.exception(ErpErrorCode.ITEM_INVALID, "物料 " + item.getCode() + " 已停用");
        }
        priceList.setItemId(item.getId());
        priceList.setItemCode(item.getCode());
        priceList.setItemName(item.getName());
        priceList.setUom(uom);
    }

    /**
     * 查询启用状态的客户。
     *
     * @param customerId 客户主键
     * @return 客户
     */
    private Customer requireCustomer(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.CUSTOMER_NOT_FOUND));
        if (!MasterDataStatus.ENABLED.equals(customer.getStatus())) {
            throw ErpErrorCode.exception(ErpErrorCode.CUSTOMER_INVALID);
        }
        return customer;
    }

    /**
     * 查询启用状态的供应商。
     *
     * @param supplierId 供应商主键
     * @return 供应商快照
     */
    private SupplierSnapshot requireSupplier(Long supplierId) {
        SupplierSnapshot supplier = supplierCatalog.findById(supplierId)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.SUPPLIER_INVALID));
        if (!MasterDataStatus.ENABLED.equals(supplier.getStatus())) {
            throw ErpErrorCode.exception(ErpErrorCode.SUPPLIER_INVALID,
                    "供应商 " + supplier.getName() + " 已停用");
        }
        return supplier;
    }
}
