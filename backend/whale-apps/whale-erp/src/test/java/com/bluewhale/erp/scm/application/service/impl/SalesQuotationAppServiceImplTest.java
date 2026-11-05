package com.bluewhale.erp.scm.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.masterdata.domain.model.Customer;
import com.bluewhale.erp.masterdata.domain.model.MasterDataStatus;
import com.bluewhale.erp.masterdata.domain.repository.CustomerRepository;
import com.bluewhale.erp.scm.application.command.SalesOrderCreateCommand;
import com.bluewhale.erp.scm.application.command.SalesQuotationCreateCommand;
import com.bluewhale.erp.scm.application.service.PriceListAppService;
import com.bluewhale.erp.scm.application.service.SalesAppService;
import com.bluewhale.erp.scm.application.view.PriceListView;
import com.bluewhale.erp.scm.application.view.SalesOrderView;
import com.bluewhale.erp.scm.application.view.SalesQuotationView;
import com.bluewhale.erp.scm.domain.model.PriceType;
import com.bluewhale.erp.scm.domain.model.SalesQuotation;
import com.bluewhale.erp.scm.domain.model.SalesQuotationLine;
import com.bluewhale.erp.scm.domain.model.ScmQuery;
import com.bluewhale.erp.scm.domain.model.ScmStatus;
import com.bluewhale.erp.scm.domain.port.ItemCatalog;
import com.bluewhale.erp.scm.domain.port.ItemSnapshot;
import com.bluewhale.erp.scm.domain.repository.SalesQuotationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SalesQuotationAppServiceImpl} 单元测试。
 *
 * @author BlueWhale
 */
class SalesQuotationAppServiceImplTest {

    private final SalesQuotationRepository quotationRepository = mock(SalesQuotationRepository.class);

    private final SalesAppService salesAppService = mock(SalesAppService.class);

    private final CustomerRepository customerRepository = mock(CustomerRepository.class);

    private final ItemCatalog itemCatalog = mock(ItemCatalog.class);

    private final PriceListAppService priceListAppService = mock(PriceListAppService.class);

    private final SalesQuotationAppServiceImpl service = new SalesQuotationAppServiceImpl(
            quotationRepository, salesAppService, customerRepository, itemCatalog, priceListAppService);

    @Test
    @DisplayName("建单：生成草稿报价单并汇总金额")
    void shouldCreateQuotation() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer()));
        when(itemCatalog.findByCode("ERP-001")).thenReturn(Optional.of(item()));
        when(quotationRepository.nextDocNo(LocalDate.of(2026, 11, 5))
        ).thenReturn("SQ-20261105-0001");

        SalesQuotationView view = service.create(command());

        assertEquals("SQ-20261105-0001", view.getDocNo());
        assertEquals(ScmStatus.DRAFT, view.getStatus());
        assertEquals("客户A", view.getCustomerName());
        assertEquals("物料A", view.getLines().get(0).getItemName());
        assertEquals(0, view.getTotalAmount().compareTo(new BigDecimal("50")));
        verify(quotationRepository).insert(any(SalesQuotation.class));
    }

    @Test
    @DisplayName("建单：单价为空时按客户售价兜底取价")
    void shouldResolveUnitPriceFromPriceList() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer()));
        when(itemCatalog.findByCode("ERP-001")).thenReturn(Optional.of(item()));
        when(quotationRepository.nextDocNo(LocalDate.of(2026, 11, 5))).thenReturn("SQ-20261105-0002");
        when(priceListAppService.resolve(eq(PriceType.SALES), eq(1L), eq(3L), any()))
                .thenReturn(priceView("12.5"));

        SalesQuotationView view = service.create(commandWithoutPrice());

        assertEquals(0, view.getLines().get(0).getUnitPrice().compareTo(new BigDecimal("12.5")));
        assertEquals(0, view.getTotalAmount().compareTo(new BigDecimal("62.5")));
        verify(quotationRepository).insert(any(SalesQuotation.class));
    }

    @Test
    @DisplayName("建单：单价为空且价目缺失时拒绝")
    void shouldRejectCreateWhenPriceMissing() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer()));
        when(itemCatalog.findByCode("ERP-001")).thenReturn(Optional.of(item()));
        when(priceListAppService.resolve(eq(PriceType.SALES), eq(1L), eq(3L), any()))
                .thenThrow(ErpErrorCode.exception(ErpErrorCode.PRICE_LIST_NOT_FOUND));

        BizException exception = assertThrows(BizException.class, () -> service.create(commandWithoutPrice()));

        assertEquals(ErpErrorCode.PRICE_LIST_NOT_FOUND, exception.getCode());
    }

    @Test
    @DisplayName("建单：停用客户拒绝")
    void shouldRejectDisabledCustomer() {
        Customer customer = customer();
        customer.setStatus(MasterDataStatus.DISABLED);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));

        BizException exception = assertThrows(BizException.class, () -> service.create(command()));

        assertEquals(ErpErrorCode.CUSTOMER_INVALID, exception.getCode());
        verify(quotationRepository, never()).insert(any(SalesQuotation.class));
    }

    @Test
    @DisplayName("接受：草稿接受为客户已接受")
    void shouldAccept() {
        when(quotationRepository.findById(2L)).thenReturn(Optional.of(draft()));

        SalesQuotationView view = service.accept(2L);

        assertEquals(ScmStatus.ACCEPTED, view.getStatus());
        verify(quotationRepository).update(any(SalesQuotation.class));
    }

    @Test
    @DisplayName("转单：生成销售订单并回写单号")
    void shouldConvert() {
        SalesQuotation quotation = draft();
        quotation.setStatus(ScmStatus.ACCEPTED);
        when(quotationRepository.findById(2L)).thenReturn(Optional.of(quotation));
        when(salesAppService.create(any())).thenReturn(orderView("SO-20261105-0001"));

        SalesQuotationView view = service.convert(2L);

        assertEquals(ScmStatus.CONVERTED, view.getStatus());
        assertEquals("SO-20261105-0001", view.getConvertedOrderNo());
        ArgumentCaptor<SalesOrderCreateCommand> captor =
                ArgumentCaptor.forClass(SalesOrderCreateCommand.class);
        verify(salesAppService).create(captor.capture());
        assertEquals(quotation.getValidUntil(), captor.getValue().getDeliveryDate());
        assertEquals(1L, captor.getValue().getCustomerId());
        assertEquals(7301L, captor.getValue().getWarehouseId());
        verify(quotationRepository).update(any(SalesQuotation.class));
    }

    @Test
    @DisplayName("转单：未接受或重复转单拒绝")
    void shouldRejectConvertWithoutAccept() {
        when(quotationRepository.findById(2L)).thenReturn(Optional.of(draft()));

        BizException exception = assertThrows(BizException.class, () -> service.convert(2L));

        assertEquals(ErpErrorCode.ORDER_STATE_CONFLICT, exception.getCode());
        verify(salesAppService, never()).create(any());
    }

    @Test
    @DisplayName("取消：草稿状态变更为已取消")
    void shouldCancel() {
        when(quotationRepository.findById(2L)).thenReturn(Optional.of(draft()));

        SalesQuotationView view = service.cancel(2L);

        assertEquals(ScmStatus.CANCELLED, view.getStatus());
        verify(quotationRepository).update(any(SalesQuotation.class));
    }

    @Test
    @DisplayName("分页查询返回总条数")
    void shouldPage() {
        when(quotationRepository.page(any())).thenReturn(List.of(draft()));
        when(quotationRepository.count(any())).thenReturn(1L);

        var page = service.page(new ScmQuery());

        assertEquals(1, page.getRecords().size());
        assertEquals(1L, page.getTotal());
    }

    @Test
    @DisplayName("详情：不存在抛业务异常")
    void shouldRejectMissingQuotation() {
        when(quotationRepository.findById(9L)).thenReturn(Optional.empty());

        BizException exception = assertThrows(BizException.class, () -> service.detail(9L));

        assertEquals(ErpErrorCode.SALES_QUOTATION_NOT_FOUND, exception.getCode());
    }

    /**
     * 构造创建命令。
     *
     * @return 命令
     */
    private SalesQuotationCreateCommand command() {
        SalesQuotationCreateCommand command = new SalesQuotationCreateCommand();
        command.setCustomerId(1L);
        command.setDocDate(LocalDate.of(2026, 11, 5));
        command.setValidUntil(LocalDate.of(2026, 11, 30));
        command.setWarehouseId(7301L);
        command.setCurrency("CNY");
        SalesQuotationCreateCommand.Line line = new SalesQuotationCreateCommand.Line();
        line.setItemCode("ERP-001");
        line.setQuantity(new BigDecimal("5"));
        line.setUnitPrice(new BigDecimal("10"));
        line.setUom("个");
        command.setLines(List.of(line));
        return command;
    }

    /**
     * 构造单价为空的创建命令。
     *
     * @return 命令
     */
    private SalesQuotationCreateCommand commandWithoutPrice() {
        SalesQuotationCreateCommand command = command();
        command.getLines().get(0).setUnitPrice(null);
        return command;
    }

    /**
     * 构造价目视图。
     *
     * @param price 单价
     * @return 视图
     */
    private PriceListView priceView(String price) {
        PriceListView view = new PriceListView();
        view.setPrice(new BigDecimal(price));
        return view;
    }

    /**
     * 构造草稿报价单。
     *
     * @return 报价单
     */
    private SalesQuotation draft() {
        SalesQuotation quotation = new SalesQuotation();
        quotation.setId(2L);
        quotation.setDocNo("SQ-001");
        quotation.setCustomerId(1L);
        quotation.setCustomerName("客户A");
        quotation.setDocDate(LocalDate.of(2026, 11, 5));
        quotation.setValidUntil(LocalDate.of(2026, 11, 30));
        quotation.setWarehouseId(7301L);
        quotation.setStatus(ScmStatus.DRAFT);
        quotation.setCurrency("CNY");
        SalesQuotationLine line = new SalesQuotationLine();
        line.setLineNo(1);
        line.setItemId(3L);
        line.setItemCode("ERP-001");
        line.setItemName("物料A");
        line.setUom("个");
        line.setQuantity(new BigDecimal("5"));
        line.setUnitPrice(new BigDecimal("10"));
        quotation.setLines(new ArrayList<>(List.of(line)));
        quotation.calculateTotal();
        return quotation;
    }

    /**
     * 构造客户。
     *
     * @return 客户
     */
    private Customer customer() {
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setCode("CUS-001");
        customer.setName("客户A");
        customer.setStatus(MasterDataStatus.ENABLED);
        return customer;
    }

    /**
     * 构造物料快照。
     *
     * @return 快照
     */
    private ItemSnapshot item() {
        ItemSnapshot item = new ItemSnapshot();
        item.setId(3L);
        item.setCode("ERP-001");
        item.setName("物料A");
        item.setStatus(MasterDataStatus.ENABLED);
        return item;
    }

    /**
     * 构造销售订单视图。
     *
     * @param docNo 订单号
     * @return 视图
     */
    private SalesOrderView orderView(String docNo) {
        SalesOrderView view = new SalesOrderView();
        view.setDocNo(docNo);
        return view;
    }
}
