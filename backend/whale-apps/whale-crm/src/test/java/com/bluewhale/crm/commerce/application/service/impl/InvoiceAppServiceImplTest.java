package com.bluewhale.crm.commerce.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.common.CrmErrorCode;
import com.bluewhale.crm.commerce.application.command.InvoiceApprovalCallbackCommand;
import com.bluewhale.crm.commerce.application.command.InvoiceRedFlushCommand;
import com.bluewhale.crm.commerce.application.command.InvoiceSaveCommand;
import com.bluewhale.crm.commerce.application.view.InvoiceView;
import com.bluewhale.crm.commerce.domain.model.ContractLedger;
import com.bluewhale.crm.commerce.domain.model.InvoiceRequest;
import com.bluewhale.crm.commerce.domain.model.InvoiceRequestType;
import com.bluewhale.crm.commerce.domain.model.InvoiceStatus;
import com.bluewhale.crm.commerce.domain.port.InvoiceApprovalPort;
import com.bluewhale.crm.commerce.domain.repository.ContractLedgerRepository;
import com.bluewhale.crm.commerce.domain.repository.InvoiceRequestRepository;
import com.bluewhale.crm.commerce.domain.repository.SalesOrderRepository;
import com.bluewhale.crm.config.CrmInvoiceApprovalProperties;
import com.bluewhale.crm.config.CrmProperties;
import com.bluewhale.crm.customer.application.service.CustomerAppService;
import com.bluewhale.crm.customer.application.view.CustomerDetailView;
import com.bluewhale.crm.customer.application.view.CustomerView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link InvoiceAppServiceImpl} 单元测试：可开票余额、审批开关、回调幂等与红冲累计校验。
 *
 * @author BlueWhale
 */
class InvoiceAppServiceImplTest {

    private static final CrmActor ACTOR = new CrmActor(7L, "tester", Set.of("crm_admin"), Set.of());

    private InvoiceRequestRepository invoiceRequestRepository;

    private ContractLedgerRepository contractLedgerRepository;

    private SalesOrderRepository salesOrderRepository;

    private CustomerAppService customerAppService;

    private InvoiceApprovalPort invoiceApprovalPort;

    private CrmInvoiceApprovalProperties approvalProperties;

    private InvoiceAppServiceImpl service;

    @BeforeEach
    void setUp() {
        invoiceRequestRepository = mock(InvoiceRequestRepository.class);
        contractLedgerRepository = mock(ContractLedgerRepository.class);
        salesOrderRepository = mock(SalesOrderRepository.class);
        customerAppService = mock(CustomerAppService.class);
        invoiceApprovalPort = mock(InvoiceApprovalPort.class);
        approvalProperties = new CrmInvoiceApprovalProperties();
        service = new InvoiceAppServiceImpl(invoiceRequestRepository, contractLedgerRepository,
                salesOrderRepository, customerAppService, invoiceApprovalPort, new CrmProperties(),
                approvalProperties);
    }

    @Test
    @DisplayName("新建开票申请：合同余额不足时拒绝")
    void shouldRejectWhenAmountExceedsAvailable() {
        when(contractLedgerRepository.findById(11L)).thenReturn(contract(100));
        when(customerAppService.detail(21L, ACTOR)).thenReturn(customerDetail());
        when(invoiceRequestRepository.sumOccupiedAmount("CONTRACT", 11L)).thenReturn(new BigDecimal("80.00"));

        BizException exception = assertThrows(BizException.class,
                () -> service.create(command(new BigDecimal("30.00")), ACTOR));

        assertEquals(CrmErrorCode.INVOICE_AMOUNT_EXCEEDED, exception.getCode());
        verify(invoiceRequestRepository, never()).insert(any());
    }

    @Test
    @DisplayName("新建开票申请：成功落草稿并返回剩余可开票金额")
    void shouldCreateDraftInvoice() {
        when(contractLedgerRepository.findById(11L)).thenReturn(contract(100));
        when(customerAppService.detail(21L, ACTOR)).thenReturn(customerDetail());
        when(invoiceRequestRepository.sumOccupiedAmount("CONTRACT", 11L)).thenReturn(new BigDecimal("10.00"));
        when(invoiceRequestRepository.maxSequence(anyString())).thenReturn(4L);

        InvoiceView view = service.create(command(new BigDecimal("30.00")), ACTOR);

        assertEquals(InvoiceStatus.DRAFT.name(), view.getStatus());
        assertEquals("INV-" + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter
                .ofPattern("yyyyMMdd")) + "-0005", view.getInvoiceNo());
        assertEquals(new BigDecimal("60.00"), view.getAvailableAmount());
        verify(invoiceRequestRepository).insert(any(InvoiceRequest.class));
    }

    @Test
    @DisplayName("提交审批：审批开关关闭时明确失败")
    void shouldRejectSubmitWhenApprovalDisabled() {
        approvalProperties.setEnabled(false);
        InvoiceRequest request = draftInvoice();
        when(invoiceRequestRepository.findById(1L)).thenReturn(request);
        when(invoiceRequestRepository.findItems(1L)).thenReturn(List.of());

        BizException exception = assertThrows(BizException.class, () -> service.submit(1L, ACTOR));

        assertEquals(CrmErrorCode.INVOICE_APPROVAL_UNAVAILABLE, exception.getCode());
        verify(invoiceApprovalPort, never()).start(anyLong(), anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("提交审批：发起流程并转待审批")
    void shouldSubmitToWorkflow() {
        approvalProperties.setEnabled(true);
        approvalProperties.setDefinitionId(66L);
        InvoiceRequest request = draftInvoice();
        when(invoiceRequestRepository.findById(1L)).thenReturn(request);
        when(invoiceRequestRepository.findItems(1L)).thenReturn(List.of());
        when(invoiceRequestRepository.sumOccupiedAmount("CONTRACT", 11L)).thenReturn(BigDecimal.ZERO);
        when(contractLedgerRepository.findById(11L)).thenReturn(contract(100));
        when(invoiceApprovalPort.start(eq(66L), anyString(), anyString(), any(), eq(ACTOR))).thenReturn(99L);

        InvoiceView view = service.submit(1L, ACTOR);

        assertEquals(InvoiceStatus.PENDING_APPROVAL.name(), view.getStatus());
        assertEquals(99L, request.getApprovalInstanceId());
        verify(invoiceRequestRepository).update(request);
    }

    @Test
    @DisplayName("审批回调：实例不存在返回 false")
    void shouldIgnoreUnknownApprovalInstance() {
        when(invoiceRequestRepository.findByApprovalInstanceId(88L)).thenReturn(null);

        assertFalse(service.handleApprovalCallback(callback(true)));
    }

    @Test
    @DisplayName("审批回调：条件更新未命中时返回 false（幂等）")
    void shouldReturnFalseWhenConditionalUpdateMissed() {
        InvoiceRequest request = draftInvoice();
        when(invoiceRequestRepository.findByApprovalInstanceId(88L)).thenReturn(request);
        when(invoiceRequestRepository.applyApprovalResult(eq(1L), eq(true), any())).thenReturn(false);

        assertFalse(service.handleApprovalCallback(callback(true)));
    }

    @Test
    @DisplayName("审批回调：条件更新命中时返回 true")
    void shouldApplyApprovalCallback() {
        InvoiceRequest request = draftInvoice();
        when(invoiceRequestRepository.findByApprovalInstanceId(88L)).thenReturn(request);
        when(invoiceRequestRepository.applyApprovalResult(eq(1L), eq(true), any())).thenReturn(true);

        assertTrue(service.handleApprovalCallback(callback(true)));
    }

    @Test
    @DisplayName("红冲：超过原单可红冲余额时拒绝")
    void shouldRejectRedFlushOverRemaining() {
        InvoiceRequest origin = issuedInvoice();
        when(invoiceRequestRepository.findById(1L)).thenReturn(origin);
        when(invoiceRequestRepository.sumRedFlushed(1L)).thenReturn(new BigDecimal("10.00"));

        InvoiceRedFlushCommand command = new InvoiceRedFlushCommand();
        command.setAmount(new BigDecimal("21.00"));
        command.setReason("部分退货");

        BizException exception = assertThrows(BizException.class, () -> service.redFlush(1L, command, ACTOR));

        assertEquals(CrmErrorCode.INVOICE_AMOUNT_EXCEEDED, exception.getCode());
    }

    @Test
    @DisplayName("红冲：生成负数红冲单并直接落已红冲")
    void shouldCreateRedFlushInvoice() {
        InvoiceRequest origin = issuedInvoice();
        when(invoiceRequestRepository.findById(1L)).thenReturn(origin);
        when(invoiceRequestRepository.sumRedFlushed(1L)).thenReturn(BigDecimal.ZERO);
        when(invoiceRequestRepository.maxSequence(anyString())).thenReturn(0L);
        when(contractLedgerRepository.findById(11L)).thenReturn(contract(100));
        when(invoiceRequestRepository.sumOccupiedAmount("CONTRACT", 11L)).thenReturn(BigDecimal.ZERO);

        InvoiceRedFlushCommand command = new InvoiceRedFlushCommand();
        command.setAmount(new BigDecimal("30.00"));
        command.setReason("客户退货");

        InvoiceView view = service.redFlush(1L, command, ACTOR);

        assertEquals(InvoiceRequestType.RED_FLUSH.name(), view.getRequestType());
        assertEquals(InvoiceStatus.RED_FLUSHED.name(), view.getStatus());
        assertEquals(new BigDecimal("-30.00"), view.getAmount());
        verify(invoiceRequestRepository).insert(any(InvoiceRequest.class));
    }

    /**
     * 构造新建命令。
     *
     * @param amount 申请金额
     * @return 命令
     */
    private InvoiceSaveCommand command(BigDecimal amount) {
        InvoiceSaveCommand command = new InvoiceSaveCommand();
        command.setSourceType("CONTRACT");
        command.setSourceId(11L);
        command.setAmount(amount);
        return command;
    }

    /**
     * 构造合同台账。
     *
     * @param amount 合同金额
     * @return 台账
     */
    private ContractLedger contract(int amount) {
        ContractLedger ledger = new ContractLedger();
        ledger.setId(11L);
        ledger.setContractNo("HT-001");
        ledger.setCustomerId(21L);
        ledger.setCurrency("CNY");
        ledger.setAmount(new BigDecimal(amount));
        return ledger;
    }

    /**
     * 构造客户详情。
     *
     * @return 客户详情
     */
    private CustomerDetailView customerDetail() {
        CustomerView customer = new CustomerView();
        customer.setId(21L);
        customer.setCustomerCode("CUS-001");
        customer.setName("蓝鲸科技");
        customer.setOwnerId(5L);
        customer.setDeptId(6L);
        CustomerDetailView detail = new CustomerDetailView();
        detail.setCustomer(customer);
        return detail;
    }

    /**
     * 构造草稿申请。
     *
     * @return 申请
     */
    private InvoiceRequest draftInvoice() {
        InvoiceRequest request = new InvoiceRequest();
        request.setId(1L);
        request.setInvoiceNo("INV-20261107-0001");
        request.setRequestType(InvoiceRequestType.NORMAL);
        request.setSourceType("CONTRACT");
        request.setSourceId(11L);
        request.setCustomerId(21L);
        request.setAmount(new BigDecimal("30.00"));
        request.setStatus(InvoiceStatus.DRAFT);
        request.setOwnerId(7L);
        return request;
    }

    /**
     * 构造已开票申请。
     *
     * @return 申请
     */
    private InvoiceRequest issuedInvoice() {
        InvoiceRequest request = draftInvoice();
        request.setStatus(InvoiceStatus.ISSUED);
        request.setIssuedAmount(new BigDecimal("30.00"));
        return request;
    }

    /**
     * 构造审批回调命令。
     *
     * @param approved 是否通过
     * @return 命令
     */
    private InvoiceApprovalCallbackCommand callback(boolean approved) {
        InvoiceApprovalCallbackCommand command = new InvoiceApprovalCallbackCommand();
        command.setInstanceId(88L);
        command.setApproved(approved);
        return command;
    }
}
