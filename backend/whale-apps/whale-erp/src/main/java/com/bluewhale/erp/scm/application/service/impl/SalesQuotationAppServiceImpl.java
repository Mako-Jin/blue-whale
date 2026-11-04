package com.bluewhale.erp.scm.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.masterdata.domain.model.Customer;
import com.bluewhale.erp.masterdata.domain.model.MasterDataStatus;
import com.bluewhale.erp.masterdata.domain.repository.CustomerRepository;
import com.bluewhale.erp.scm.application.command.SalesOrderCreateCommand;
import com.bluewhale.erp.scm.application.command.SalesQuotationCreateCommand;
import com.bluewhale.erp.scm.application.service.SalesAppService;
import com.bluewhale.erp.scm.application.service.SalesQuotationAppService;
import com.bluewhale.erp.scm.application.view.SalesOrderView;
import com.bluewhale.erp.scm.application.view.SalesQuotationView;
import com.bluewhale.erp.scm.domain.model.SalesQuotation;
import com.bluewhale.erp.scm.domain.model.SalesQuotationLine;
import com.bluewhale.erp.scm.domain.model.ScmQuery;
import com.bluewhale.erp.scm.domain.model.ScmStatus;
import com.bluewhale.erp.scm.domain.port.ItemCatalog;
import com.bluewhale.erp.scm.domain.port.ItemSnapshot;
import com.bluewhale.erp.scm.domain.repository.SalesQuotationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 销售报价应用服务实现。
 *
 * <p>转单闭环：客户已接受报价单 → 草稿销售订单（不改写订单状态、不触发记账），
 * 报价单回写 {@code convertedOrderNo} 并置 {@code CONVERTED}。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class SalesQuotationAppServiceImpl implements SalesQuotationAppService {

    /** 默认币种 */
    private static final String DEFAULT_CURRENCY = "CNY";

    private final SalesQuotationRepository quotationRepository;

    private final SalesAppService salesAppService;

    private final CustomerRepository customerRepository;

    private final ItemCatalog itemCatalog;

    @Override
    public PageResult<SalesQuotationView> page(ScmQuery query) {
        List<SalesQuotationView> records = quotationRepository.page(query).stream()
                .map(SalesQuotationView::of).toList();
        return PageResult.of(records, quotationRepository.count(query), query.getCurrent(), query.getSize());
    }

    @Override
    public SalesQuotationView detail(Long id) {
        return SalesQuotationView.of(requireQuotation(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesQuotationView create(SalesQuotationCreateCommand command) {
        Customer customer = requireCustomer(command.getCustomerId());
        SalesQuotation quotation = new SalesQuotation();
        quotation.setDocNo(quotationRepository.nextDocNo(command.getDocDate()));
        quotation.setCustomerId(customer.getId());
        quotation.setCustomerName(customer.getName());
        quotation.setDocDate(command.getDocDate());
        quotation.setValidUntil(command.getValidUntil());
        quotation.setWarehouseId(command.getWarehouseId());
        quotation.setStatus(ScmStatus.DRAFT);
        quotation.setCurrency(StringUtils.hasText(command.getCurrency())
                ? command.getCurrency() : DEFAULT_CURRENCY);
        quotation.setRemark(command.getRemark());
        List<SalesQuotationLine> lines = new ArrayList<>();
        int lineNo = 1;
        for (SalesQuotationCreateCommand.Line lineCommand : command.getLines()) {
            ItemSnapshot item = requireItem(lineCommand.getItemCode());
            SalesQuotationLine line = new SalesQuotationLine();
            line.setLineNo(lineNo++);
            line.setItemId(item.getId());
            line.setItemCode(item.getCode());
            line.setItemName(item.getName());
            line.setUom(lineCommand.getUom());
            line.setQuantity(lineCommand.getQuantity());
            line.setUnitPrice(lineCommand.getUnitPrice());
            lines.add(line);
        }
        quotation.setLines(lines);
        quotation.calculateTotal();
        quotationRepository.insert(quotation);
        return SalesQuotationView.of(quotation);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesQuotationView accept(Long id) {
        SalesQuotation quotation = requireQuotation(id);
        quotation.accept();
        quotationRepository.update(quotation);
        return SalesQuotationView.of(quotation);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesQuotationView convert(Long id) {
        SalesQuotation quotation = requireQuotation(id);
        if (!ScmStatus.ACCEPTED.equals(quotation.getStatus())) {
            throw ErpErrorCode.exception(ErpErrorCode.ORDER_STATE_CONFLICT, "只有客户已接受的销售报价单可以转单");
        }
        SalesOrderCreateCommand command = new SalesOrderCreateCommand();
        command.setCustomerId(quotation.getCustomerId());
        command.setDocDate(LocalDate.now());
        command.setDeliveryDate(quotation.getValidUntil());
        command.setWarehouseId(quotation.getWarehouseId());
        command.setCurrency(quotation.getCurrency());
        command.setRemark("销售报价 " + quotation.getDocNo());
        List<SalesOrderCreateCommand.Line> lines = new ArrayList<>();
        for (SalesQuotationLine line : quotation.getLines()) {
            SalesOrderCreateCommand.Line lineCommand = new SalesOrderCreateCommand.Line();
            lineCommand.setItemCode(line.getItemCode());
            lineCommand.setQuantity(line.getQuantity());
            lineCommand.setUnitPrice(line.getUnitPrice());
            lineCommand.setUom(line.getUom());
            lines.add(lineCommand);
        }
        command.setLines(lines);
        SalesOrderView order = salesAppService.create(command);
        quotation.convert(order.getDocNo());
        quotationRepository.update(quotation);
        return SalesQuotationView.of(quotation);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesQuotationView cancel(Long id) {
        SalesQuotation quotation = requireQuotation(id);
        quotation.cancel();
        quotationRepository.update(quotation);
        return SalesQuotationView.of(quotation);
    }

    /**
     * 按主键查询报价单，不存在抛业务异常。
     *
     * @param id 主键
     * @return 销售报价单
     */
    private SalesQuotation requireQuotation(Long id) {
        return quotationRepository.findById(id)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.SALES_QUOTATION_NOT_FOUND));
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
     * 查询启用状态的物料。
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
}
