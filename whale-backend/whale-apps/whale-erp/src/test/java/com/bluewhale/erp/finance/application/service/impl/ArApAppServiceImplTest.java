package com.bluewhale.erp.finance.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.finance.application.command.PayableCreateCommand;
import com.bluewhale.erp.finance.application.command.ReceivableCreateCommand;
import com.bluewhale.erp.finance.application.command.SettleCommand;
import com.bluewhale.erp.finance.application.view.AgingView;
import com.bluewhale.erp.finance.application.view.CustomerReceivableView;
import com.bluewhale.erp.finance.application.view.PayableView;
import com.bluewhale.erp.finance.application.view.ReceivableView;
import com.bluewhale.erp.finance.domain.model.ArApQuery;
import com.bluewhale.erp.finance.domain.model.FinanceStatus;
import com.bluewhale.erp.finance.domain.model.Payable;
import com.bluewhale.erp.finance.domain.model.PayableSettlement;
import com.bluewhale.erp.finance.domain.model.Receivable;
import com.bluewhale.erp.finance.domain.model.ReceivableSettlement;
import com.bluewhale.erp.finance.domain.repository.PayableRepository;
import com.bluewhale.erp.finance.domain.repository.ReceivableRepository;
import com.bluewhale.erp.masterdata.domain.model.Customer;
import com.bluewhale.erp.masterdata.domain.model.MasterDataStatus;
import com.bluewhale.erp.masterdata.domain.repository.CustomerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ArApAppServiceImpl} 单元测试。
 *
 * @author BlueWhale
 */
class ArApAppServiceImplTest {

    private final ReceivableRepository receivableRepository = mock(ReceivableRepository.class);

    private final PayableRepository payableRepository = mock(PayableRepository.class);

    private final CustomerRepository customerRepository = mock(CustomerRepository.class);

    private final ArApAppServiceImpl service = new ArApAppServiceImpl(
            receivableRepository, payableRepository, customerRepository);

    @Test
    @DisplayName("应收与应付分页映射")
    void shouldPageLedgers() {
        Receivable receivable = receivable("AR-001", 7201L, "客户A", "1000.00");
        Payable payable = payable("AP-001", 9001L, "供应商A", "800.00");
        when(receivableRepository.page(any())).thenReturn(List.of(receivable));
        when(receivableRepository.count(any())).thenReturn(1L);
        when(payableRepository.page(any())).thenReturn(List.of(payable));
        when(payableRepository.count(any())).thenReturn(1L);

        PageResult<ReceivableView> receivablePage = service.receivablePage(new ArApQuery());
        PageResult<PayableView> payablePage = service.payablePage(new ArApQuery());

        assertEquals(1, receivablePage.getRecords().size());
        assertEquals(new BigDecimal("1000.00"), receivablePage.getRecords().get(0).getRemainingAmount());
        assertEquals(1, payablePage.getRecords().size());
        assertEquals("供应商A", payablePage.getRecords().get(0).getSupplierName());
    }

    @Test
    @DisplayName("新增应收单成功并快照客户名称")
    void shouldCreateReceivable() {
        when(customerRepository.findById(7201L)).thenReturn(Optional.of(customer(7201L, "客户A")));
        when(receivableRepository.findByDocNo("AR-001")).thenReturn(Optional.empty());

        ReceivableView view = service.createReceivable(receivableCommand(7201L, "AR-001", "1000.00"));

        assertEquals("AR-001", view.getDocNo());
        assertEquals("客户A", view.getCustomerName());
        assertEquals("CNY", view.getCurrency());
        assertEquals(FinanceStatus.LEDGER_OPEN, view.getStatus());
        assertEquals(new BigDecimal("1000.00"), view.getRemainingAmount());
        verify(receivableRepository).insert(any(Receivable.class));
    }

    @Test
    @DisplayName("单据号为空时自动生成 AR 前缀")
    void shouldGenerateReceivableDocNo() {
        when(customerRepository.findById(7201L)).thenReturn(Optional.of(customer(7201L, "客户A")));

        ReceivableView view = service.createReceivable(receivableCommand(7201L, null, "1000.00"));

        assertTrue(view.getDocNo().startsWith("AR-"));
        assertEquals("AR-".length() + 12, view.getDocNo().length());
    }

    @Test
    @DisplayName("客户不存在或停用时拒绝新增应收")
    void shouldRejectInvalidCustomer() {
        when(customerRepository.findById(7201L)).thenReturn(Optional.empty());
        BizException missing = assertThrows(BizException.class,
                () -> service.createReceivable(receivableCommand(7201L, "AR-001", "1000.00")));
        assertEquals(ErpErrorCode.CUSTOMER_NOT_FOUND, missing.getCode());

        Customer disabled = customer(7202L, "客户B");
        disabled.setStatus(MasterDataStatus.DISABLED);
        when(customerRepository.findById(7202L)).thenReturn(Optional.of(disabled));
        BizException invalid = assertThrows(BizException.class,
                () -> service.createReceivable(receivableCommand(7202L, "AR-002", "1000.00")));
        assertEquals(ErpErrorCode.CUSTOMER_INVALID, invalid.getCode());
    }

    @Test
    @DisplayName("金额非正与日期倒挂时拒绝新增应收")
    void shouldValidateReceivableAmountAndDates() {
        when(customerRepository.findById(7201L)).thenReturn(Optional.of(customer(7201L, "客户A")));
        BizException amountException = assertThrows(BizException.class,
                () -> service.createReceivable(receivableCommand(7201L, "AR-001", "0")));
        assertEquals(ErpErrorCode.AMOUNT_INVALID, amountException.getCode());

        ReceivableCreateCommand reversedDate = receivableCommand(7201L, "AR-001", "1000.00");
        reversedDate.setDueDate(reversedDate.getDocDate().minusDays(1));
        BizException dateException = assertThrows(BizException.class,
                () -> service.createReceivable(reversedDate));
        assertEquals(ErpErrorCode.DOC_DATE_INVALID, dateException.getCode());
        verify(receivableRepository, never()).insert(any(Receivable.class));
    }

    @Test
    @DisplayName("应收单号重复时拒绝")
    void shouldRejectDuplicateReceivableDocNo() {
        when(customerRepository.findById(7201L)).thenReturn(Optional.of(customer(7201L, "客户A")));
        when(receivableRepository.findByDocNo("AR-001"))
                .thenReturn(Optional.of(receivable("AR-001", 7201L, "客户A", "1000.00")));

        BizException exception = assertThrows(BizException.class,
                () -> service.createReceivable(receivableCommand(7201L, "AR-001", "1000.00")));

        assertEquals(ErpErrorCode.DOC_NO_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("部分核销应收并写入核销流水")
    void shouldSettleReceivable() {
        Receivable receivable = receivable("AR-001", 7201L, "客户A", "1000.00");
        receivable.setId(5L);
        when(receivableRepository.findById(5L)).thenReturn(Optional.of(receivable));

        SettleCommand command = settleCommand("300.00");
        ReceivableView view = service.settleReceivable(5L, command);

        assertEquals(FinanceStatus.LEDGER_PARTIALLY_SETTLED, view.getStatus());
        assertEquals(new BigDecimal("700.00"), view.getRemainingAmount());
        verify(receivableRepository).insertSettlement(any(ReceivableSettlement.class));
        verify(receivableRepository).update(receivable);
    }

    @Test
    @DisplayName("应收单不存在时核销抛出 NOT_FOUND")
    void shouldRejectMissingReceivable() {
        when(receivableRepository.findById(5L)).thenReturn(Optional.empty());

        BizException exception = assertThrows(BizException.class,
                () -> service.settleReceivable(5L, settleCommand("300.00")));

        assertEquals(ErpErrorCode.RECEIVABLE_NOT_FOUND, exception.getCode());
        verify(receivableRepository, never()).insertSettlement(any());
    }

    @Test
    @DisplayName("坏账计提到位后状态为坏账核销")
    void shouldProvisionBadDebt() {
        Receivable receivable = receivable("AR-001", 7201L, "客户A", "1000.00");
        receivable.setId(5L);
        when(receivableRepository.findById(5L)).thenReturn(Optional.of(receivable));

        ReceivableView view = service.provisionBadDebt(5L, settleCommand("1000.00"));

        assertEquals(FinanceStatus.LEDGER_WRITTEN_OFF, view.getStatus());
        verify(receivableRepository).update(receivable);
        verify(receivableRepository, never()).insertSettlement(any());
    }

    @Test
    @DisplayName("新增应付单默认供应商名称快照")
    void shouldCreatePayable() {
        PayableCreateCommand command = payableCommand(9001L, null, "800.00");
        command.setSupplierName(null);

        PayableView view = service.createPayable(command);

        assertEquals("供应商-9001", view.getSupplierName());
        assertEquals("CNY", view.getCurrency());
        assertEquals(FinanceStatus.LEDGER_OPEN, view.getStatus());
        assertTrue(view.getDocNo().startsWith("AP-"));
        verify(payableRepository).insert(any(Payable.class));
    }

    @Test
    @DisplayName("应付单号重复与金额非法时拒绝")
    void shouldRejectInvalidPayable() {
        when(payableRepository.findByDocNo("AP-001"))
                .thenReturn(Optional.of(payable("AP-001", 9001L, "供应商A", "800.00")));
        BizException conflict = assertThrows(BizException.class,
                () -> service.createPayable(payableCommand(9001L, "AP-001", "800.00")));
        assertEquals(ErpErrorCode.DOC_NO_CONFLICT, conflict.getCode());

        BizException amountException = assertThrows(BizException.class,
                () -> service.createPayable(payableCommand(9001L, "AP-002", "-1")));
        assertEquals(ErpErrorCode.AMOUNT_INVALID, amountException.getCode());
        verify(payableRepository, never()).insert(any(Payable.class));
    }

    @Test
    @DisplayName("应付付款核销与不存在场景")
    void shouldSettlePayable() {
        Payable payable = payable("AP-001", 9001L, "供应商A", "800.00");
        payable.setId(6L);
        when(payableRepository.findById(6L)).thenReturn(Optional.of(payable));

        PayableView view = service.settlePayable(6L, settleCommand("800.00"));

        assertEquals(FinanceStatus.LEDGER_SETTLED, view.getStatus());
        assertEquals(0, view.getRemainingAmount().compareTo(BigDecimal.ZERO));
        verify(payableRepository).insertSettlement(any(PayableSettlement.class));
        verify(payableRepository).update(payable);

        when(payableRepository.findById(7L)).thenReturn(Optional.empty());
        BizException missing = assertThrows(BizException.class,
                () -> service.settlePayable(7L, settleCommand("100.00")));
        assertEquals(ErpErrorCode.PAYABLE_NOT_FOUND, missing.getCode());
    }

    @Test
    @DisplayName("应收账龄按客户聚合并按未结余额倒序")
    void shouldReceivableAging() {
        Receivable small = receivable("AR-001", 7201L, "客户A", "100.00");
        small.setDueDate(LocalDate.now().plusDays(5));
        Receivable large = receivable("AR-002", 7202L, "客户B", "900.00");
        large.setDueDate(LocalDate.now().minusDays(45));
        when(receivableRepository.listOutstanding()).thenReturn(List.of(small, large));

        List<AgingView> aging = service.receivableAging();

        assertEquals(2, aging.size());
        assertEquals("客户B", aging.get(0).getPartyName());
        assertEquals(new BigDecimal("900.00"), aging.get(0).getTotal());
        assertEquals(new BigDecimal("900.00"), aging.get(0).getOverdue31To60());
        assertEquals(new BigDecimal("100.00"), aging.get(1).getNotDue());
    }

    @Test
    @DisplayName("按客户编码查询未结应收并分桶，客户不存在返回空摘要")
    void shouldCustomerReceivable() {
        Customer customer = customer(7201L, "客户A");
        customer.setCode("CUS-2026-000001");
        when(customerRepository.findByCode("CUS-2026-000001")).thenReturn(Optional.of(customer));
        Receivable notDue = receivable("AR-001", 7201L, "客户A", "100.00");
        notDue.setDueDate(LocalDate.now().plusDays(5));
        Receivable overdue = receivable("AR-002", 7201L, "客户A", "900.00");
        overdue.setDueDate(LocalDate.now().minusDays(45));
        when(receivableRepository.listOutstandingByCustomer(7201L)).thenReturn(List.of(notDue, overdue));

        CustomerReceivableView view = service.customerReceivable("CUS-2026-000001");

        assertEquals(7201L, view.getCustomerId());
        assertEquals("CUS-2026-000001", view.getCustomerCode());
        assertEquals(2, view.getReceivableCount());
        assertEquals(new BigDecimal("1000.00"), view.getTotalRemaining());
        assertEquals(new BigDecimal("100.00"), view.getNotDue());
        assertEquals(new BigDecimal("900.00"), view.getOverdue31To60());
        assertEquals(2, view.getReceivables().size());

        when(customerRepository.findByCode("CUS-MISSING")).thenReturn(Optional.empty());
        CustomerReceivableView missing = service.customerReceivable("CUS-MISSING");
        assertEquals(0, missing.getReceivableCount());
        assertEquals(new BigDecimal("0"), missing.getTotalRemaining());
    }

    @Test
    @DisplayName("应付账龄按供应商聚合")
    void shouldPayableAging() {
        Payable payable = payable("AP-001", 9001L, "供应商A", "800.00");
        payable.setDueDate(LocalDate.now().minusDays(100));
        when(payableRepository.listOutstanding()).thenReturn(List.of(payable));

        List<AgingView> aging = service.payableAging();

        assertEquals(1, aging.size());
        assertEquals("AP", aging.get(0).getDimension());
        assertEquals(new BigDecimal("800.00"), aging.get(0).getOverdueOver90());
    }

    /**
     * 构造客户。
     *
     * @param id 主键
     * @param name 名称
     * @return 客户
     */
    private static Customer customer(Long id, String name) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setName(name);
        customer.setStatus(MasterDataStatus.ENABLED);
        customer.setCreditLimit(BigDecimal.ZERO);
        return customer;
    }

    /**
     * 构造应收单。
     *
     * @param docNo 单号
     * @param customerId 客户 ID
     * @param customerName 客户名称
     * @param amount 金额
     * @return 应收单
     */
    private static Receivable receivable(String docNo, Long customerId, String customerName,
                                         String amount) {
        Receivable receivable = new Receivable();
        receivable.setDocNo(docNo);
        receivable.setCustomerId(customerId);
        receivable.setCustomerName(customerName);
        receivable.setDocDate(LocalDate.now().minusDays(30));
        receivable.setDueDate(LocalDate.now());
        receivable.setCurrency("CNY");
        receivable.setAmount(new BigDecimal(amount));
        receivable.setSettledAmount(BigDecimal.ZERO);
        receivable.setBadDebtAmount(BigDecimal.ZERO);
        receivable.setStatus(FinanceStatus.LEDGER_OPEN);
        return receivable;
    }

    /**
     * 构造应收命令。
     *
     * @param customerId 客户 ID
     * @param docNo 单号
     * @param amount 金额
     * @return 命令
     */
    private static ReceivableCreateCommand receivableCommand(Long customerId, String docNo,
                                                             String amount) {
        ReceivableCreateCommand command = new ReceivableCreateCommand();
        command.setCustomerId(customerId);
        command.setDocNo(docNo);
        command.setDocDate(LocalDate.now());
        command.setDueDate(LocalDate.now().plusDays(30));
        command.setAmount(new BigDecimal(amount));
        return command;
    }

    /**
     * 构造应付单。
     *
     * @param docNo 单号
     * @param supplierId 供应商 ID
     * @param supplierName 供应商名称
     * @param amount 金额
     * @return 应付单
     */
    private static Payable payable(String docNo, Long supplierId, String supplierName, String amount) {
        Payable payable = new Payable();
        payable.setDocNo(docNo);
        payable.setSupplierId(supplierId);
        payable.setSupplierName(supplierName);
        payable.setDocDate(LocalDate.now().minusDays(30));
        payable.setDueDate(LocalDate.now());
        payable.setCurrency("CNY");
        payable.setAmount(new BigDecimal(amount));
        payable.setSettledAmount(BigDecimal.ZERO);
        payable.setStatus(FinanceStatus.LEDGER_OPEN);
        return payable;
    }

    /**
     * 构造应付命令。
     *
     * @param supplierId 供应商 ID
     * @param docNo 单号
     * @param amount 金额
     * @return 命令
     */
    private static PayableCreateCommand payableCommand(Long supplierId, String docNo, String amount) {
        PayableCreateCommand command = new PayableCreateCommand();
        command.setSupplierId(supplierId);
        command.setSupplierName("供应商A");
        command.setDocNo(docNo);
        command.setDocDate(LocalDate.now());
        command.setDueDate(LocalDate.now().plusDays(30));
        command.setAmount(new BigDecimal(amount));
        return command;
    }

    /**
     * 构造核销命令。
     *
     * @param amount 金额
     * @return 命令
     */
    private static SettleCommand settleCommand(String amount) {
        SettleCommand command = new SettleCommand();
        command.setAmount(new BigDecimal(amount));
        command.setSettleType(FinanceStatus.SETTLE_BANK);
        return command;
    }
}
