package com.bluewhale.oa.expense.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.oa.approval.application.service.ApprovalInstanceAppService;
import com.bluewhale.oa.approval.application.view.ApprovalInstanceView;
import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.common.application.service.SequenceAppService;
import com.bluewhale.oa.expense.application.command.ExpenseCommands;
import com.bluewhale.oa.expense.application.view.ExpenseViews;
import com.bluewhale.oa.expense.domain.gateway.ErpPayableGateway;
import com.bluewhale.oa.expense.domain.model.ErpPushStatus;
import com.bluewhale.oa.expense.domain.model.Expense;
import com.bluewhale.oa.expense.domain.model.ExpenseCategory;
import com.bluewhale.oa.expense.domain.model.ExpenseStandard;
import com.bluewhale.oa.expense.domain.model.ExpenseStatus;
import com.bluewhale.oa.expense.domain.repository.ExpenseCategoryRepository;
import com.bluewhale.oa.expense.domain.repository.ExpenseRepository;
import com.bluewhale.oa.integration.application.command.OutboxAppendCommand;
import com.bluewhale.oa.integration.application.service.OutboxAppService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ExpenseAppServiceImpl} 报销闭环单元测试：合计重算、发票查重、费用标准、
 * 审批通过 Outbox 幂等、ERP 推送失败标记。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class ExpenseAppServiceImplTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private ExpenseCategoryRepository categoryRepository;

    @Mock
    private ApprovalInstanceAppService approvalInstanceAppService;

    @Mock
    private OutboxAppService outboxAppService;

    @Mock
    private SequenceAppService sequenceAppService;

    @Mock
    private ErpPayableGateway erpPayableGateway;

    private ExpenseAppServiceImpl expenseAppService;

    /**
     * 构造被测服务。
     */
    @BeforeEach
    void setUp() {
        expenseAppService = new ExpenseAppServiceImpl(expenseRepository, categoryRepository,
                approvalInstanceAppService, outboxAppService, sequenceAppService, erpPayableGateway);
    }

    @Test
    @DisplayName("create：合计按明细重算，登记审批并输出条件变量")
    void shouldRecalculateTotalAndRegisterApproval() {
        when(sequenceAppService.nextCode(anyString(), anyString())).thenReturn("EXP-202611-001");
        when(categoryRepository.findCategoryByCode("TRAVEL")).thenReturn(Optional.of(category("TRAVEL")));
        when(categoryRepository.findStandard(1L, "DEFAULT")).thenReturn(Optional.of(standard("2000")));
        when(expenseRepository.existsInvoice(any(), anyString(), any())).thenReturn(false);
        when(approvalInstanceAppService.register(any())).thenReturn(instance(501L));

        ExpenseViews.ExpenseView view = expenseAppService.create(command());

        ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);
        verify(expenseRepository).insert(captor.capture());
        Expense saved = captor.getValue();
        assertEquals(new BigDecimal("300.00"), saved.getTotalAmount());
        assertEquals(ExpenseStatus.PENDING, saved.getStatus());
        assertEquals(501L, view.getApprovalInstanceId());
        Map<String, Object> variables = view.getApprovalVariables();
        assertEquals(new BigDecimal("300.00"), variables.get("amount"));
        assertEquals("TRAVEL", variables.get("categoryCode"));
        assertEquals(10L, variables.get("deptId"));
    }

    @Test
    @DisplayName("create：发票在单据内重复时拒绝")
    void shouldRejectDuplicatedInvoiceInSameDoc() {
        when(sequenceAppService.nextCode(anyString(), anyString())).thenReturn("EXP-202611-001");
        when(categoryRepository.findCategoryByCode("TRAVEL")).thenReturn(Optional.of(category("TRAVEL")));
        when(categoryRepository.findStandard(1L, "DEFAULT")).thenReturn(Optional.of(standard("2000")));

        ExpenseCommands.ExpenseSaveCommand command = command();
        command.setInvoices(List.of(invoice("FP001"), invoice("FP001")));

        BizException exception = assertThrows(BizException.class,
                () -> expenseAppService.create(command));

        assertEquals(OaErrorCode.EXPENSE_INVOICE_DUPLICATE, exception.getCode());
        verify(expenseRepository, never()).insert(any());
    }

    @Test
    @DisplayName("create：单笔金额超过费用标准时拒绝")
    void shouldRejectWhenStandardExceeded() {
        when(sequenceAppService.nextCode(anyString(), anyString())).thenReturn("EXP-202611-001");
        when(categoryRepository.findCategoryByCode("TRAVEL")).thenReturn(Optional.of(category("TRAVEL")));
        when(categoryRepository.findStandard(1L, "DEFAULT")).thenReturn(Optional.of(standard("100")));

        BizException exception = assertThrows(BizException.class,
                () -> expenseAppService.create(command()));

        assertEquals(OaErrorCode.EXPENSE_STANDARD_EXCEEDED, exception.getCode());
        verify(expenseRepository, never()).insert(any());
    }

    @Test
    @DisplayName("updateStatus：审批通过追加 Outbox，重复终态只追加一次")
    void shouldAppendApprovedEventOnce() {
        when(expenseRepository.findById(1L)).thenReturn(Optional.of(pending()));

        expenseAppService.updateStatus(1L, status("APPROVED"));
        expenseAppService.updateStatus(1L, status("APPROVED"));

        ArgumentCaptor<OutboxAppendCommand> captor = ArgumentCaptor.forClass(OutboxAppendCommand.class);
        verify(outboxAppService, times(1)).append(captor.capture());
        assertEquals("OA_EXPENSE_APPROVED", captor.getValue().getEventType());
        assertEquals("1", captor.getValue().getAggregateId());
    }

    @Test
    @DisplayName("pushPending：ERP 不可用时标记失败且不抛出")
    void shouldMarkFailedWhenErpUnavailable() {
        when(expenseRepository.listPushable(anyInt())).thenReturn(List.of(approved()));
        when(erpPayableGateway.createPayable(any()))
                .thenThrow(new BizException(OaErrorCode.EXPENSE_ERP_UNAVAILABLE, "ERP 不可用"));

        int success = expenseAppService.pushPending();

        assertEquals(0, success);
        ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);
        verify(expenseRepository).update(captor.capture());
        assertEquals(ErpPushStatus.FAILED, captor.getValue().getErpPushStatus());
    }

    @Test
    @DisplayName("retryPush：推送成功回写应付单号并置为 SENT")
    void shouldWritePayableNoWhenPushSucceeds() {
        Expense expense = approved();
        when(expenseRepository.findById(1L)).thenReturn(Optional.of(expense));
        when(erpPayableGateway.createPayable(any())).thenReturn("AP-202611-001");

        ExpenseViews.ExpenseView view = expenseAppService.retryPush(1L);

        assertEquals("AP-202611-001", view.getErpPayableNo());
        assertEquals(ErpPushStatus.SENT.name(), view.getErpPushStatus());
        verify(expenseRepository).update(any());
    }

    @Test
    @DisplayName("retryPush：未审批通过禁止推送")
    void shouldRejectPushWhenNotApproved() {
        when(expenseRepository.findById(1L)).thenReturn(Optional.of(pending()));

        BizException exception = assertThrows(BizException.class,
                () -> expenseAppService.retryPush(1L));

        assertEquals(OaErrorCode.EXPENSE_STATUS_CONFLICT, exception.getCode());
        verify(erpPayableGateway, never()).createPayable(any());
    }

    /**
     * 构造报销保存命令。
     *
     * @return 保存命令
     */
    private ExpenseCommands.ExpenseSaveCommand command() {
        ExpenseCommands.ExpenseSaveCommand command = new ExpenseCommands.ExpenseSaveCommand();
        command.setApplicant("zhangsan");
        command.setApplicantId(100L);
        command.setDeptId(10L);
        command.setCategoryCode("TRAVEL");
        command.setTitle("客户走访差旅");
        command.setCurrency("CNY");
        command.setItems(List.of(item("TRAVEL", "200"), item("TRAVEL", "100")));
        command.setInvoices(List.of(invoice("FP001")));
        return command;
    }

    /**
     * 构造明细命令。
     *
     * @param categoryCode 费用类别编码
     * @param amount 金额
     * @return 明细命令
     */
    private ExpenseCommands.ExpenseItemCommand item(String categoryCode, String amount) {
        ExpenseCommands.ExpenseItemCommand item = new ExpenseCommands.ExpenseItemCommand();
        item.setCategoryCode(categoryCode);
        item.setExpenseDate(LocalDate.of(2026, 11, 1));
        item.setAmount(new BigDecimal(amount));
        item.setTaxAmount(BigDecimal.ZERO);
        item.setDescription("差旅");
        return item;
    }

    /**
     * 构造发票命令。
     *
     * @param invoiceNo 发票号码
     * @return 发票命令
     */
    private ExpenseCommands.ExpenseInvoiceCommand invoice(String invoiceNo) {
        ExpenseCommands.ExpenseInvoiceCommand invoice = new ExpenseCommands.ExpenseInvoiceCommand();
        invoice.setInvoiceCode("033001");
        invoice.setInvoiceNo(invoiceNo);
        invoice.setInvoiceDate(LocalDate.of(2026, 11, 1));
        invoice.setAmount(new BigDecimal("300"));
        invoice.setTaxAmount(BigDecimal.ZERO);
        return invoice;
    }

    /**
     * 构造费用类别。
     *
     * @param code 类别编码
     * @return 费用类别
     */
    private ExpenseCategory category(String code) {
        ExpenseCategory category = new ExpenseCategory();
        category.setId(1L);
        category.setCode(code);
        category.setName("差旅费");
        category.setEnabled(true);
        return category;
    }

    /**
     * 构造费用标准。
     *
     * @param limit 限额
     * @return 费用标准
     */
    private ExpenseStandard standard(String limit) {
        ExpenseStandard standard = new ExpenseStandard();
        standard.setId(2L);
        standard.setCategoryId(1L);
        standard.setLevelCode("DEFAULT");
        standard.setLimitAmount(new BigDecimal(limit));
        standard.setEnabled(true);
        return standard;
    }

    /**
     * 构造审批实例视图。
     *
     * @param id 实例ID
     * @return 审批实例视图
     */
    private ApprovalInstanceView instance(Long id) {
        ApprovalInstanceView view = new ApprovalInstanceView();
        view.setId(id);
        return view;
    }

    /**
     * 构造待审批报销单。
     *
     * @return 报销单
     */
    private Expense pending() {
        Expense expense = new Expense();
        expense.setId(1L);
        expense.setCode("EXP-202611-001");
        expense.setApplicant("zhangsan");
        expense.setApplicantId(100L);
        expense.setDeptId(10L);
        expense.setCategoryCode("TRAVEL");
        expense.setTitle("客户走访差旅");
        expense.setTotalAmount(new BigDecimal("300.00"));
        expense.setCurrency("CNY");
        expense.setStatus(ExpenseStatus.PENDING);
        expense.setErpPushStatus(ErpPushStatus.NONE);
        return expense;
    }

    /**
     * 构造已审批通过的报销单。
     *
     * @return 报销单
     */
    private Expense approved() {
        Expense expense = pending();
        expense.setStatus(ExpenseStatus.APPROVED);
        expense.setErpPushStatus(ErpPushStatus.PENDING);
        return expense;
    }

    /**
     * 构造状态命令。
     *
     * @param status 目标状态
     * @return 状态命令
     */
    private ExpenseCommands.ExpenseStatusCommand status(String status) {
        ExpenseCommands.ExpenseStatusCommand command = new ExpenseCommands.ExpenseStatusCommand();
        command.setStatus(status);
        return command;
    }
}
