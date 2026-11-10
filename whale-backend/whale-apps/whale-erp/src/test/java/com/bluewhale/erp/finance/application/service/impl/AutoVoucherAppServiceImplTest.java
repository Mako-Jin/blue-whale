package com.bluewhale.erp.finance.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.common.ErpFinanceAccountProperties;
import com.bluewhale.erp.finance.application.command.AutoVoucherCommand;
import com.bluewhale.erp.finance.domain.model.FinanceStatus;
import com.bluewhale.erp.finance.domain.model.Voucher;
import com.bluewhale.erp.finance.domain.repository.VoucherRepository;
import com.bluewhale.erp.masterdata.domain.model.Account;
import com.bluewhale.erp.masterdata.domain.model.AccountingPeriod;
import com.bluewhale.erp.masterdata.domain.model.MasterDataStatus;
import com.bluewhale.erp.masterdata.domain.repository.AccountRepository;
import com.bluewhale.erp.masterdata.domain.repository.AccountingPeriodRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AutoVoucherAppServiceImpl} 单元测试。
 *
 * @author BlueWhale
 */
class AutoVoucherAppServiceImplTest {

    private final VoucherRepository voucherRepository = mock(VoucherRepository.class);

    private final AccountRepository accountRepository = mock(AccountRepository.class);

    private final AccountingPeriodRepository periodRepository = mock(AccountingPeriodRepository.class);

    private final ErpFinanceAccountProperties properties = new ErpFinanceAccountProperties();

    private final AutoVoucherAppServiceImpl service = new AutoVoucherAppServiceImpl(voucherRepository,
            accountRepository, periodRepository, properties);

    @Test
    @DisplayName("采购收货生成两行凭证并直接记账")
    void shouldPostReceiptVoucher() {
        stubOpenPeriod();
        stubAccounts();
        when(voucherRepository.nextVoucherNo("202611")).thenReturn("V-001");
        doAnswer(invocation -> {
            Voucher voucher = invocation.getArgument(0);
            voucher.setId(10L);
            return null;
        }).when(voucherRepository).insert(any(Voucher.class));

        Long voucherId = service.postReceipt(command(false, "100"));

        assertEquals(10L, voucherId);
        ArgumentCaptor<Voucher> captor = ArgumentCaptor.forClass(Voucher.class);
        verify(voucherRepository).insert(captor.capture());
        Voucher voucher = captor.getValue();
        assertEquals(2, voucher.getLines().size());
        assertEquals(FinanceStatus.VOUCHER_POSTED, voucher.getStatus());
        assertEquals("GOODS_RECEIPT", voucher.getSourceType());
        assertEquals("GR-001", voucher.getSourceId());
        assertEquals(0, voucher.getTotalDebit().compareTo(new BigDecimal("100")));
        assertEquals(0, voucher.getTotalCredit().compareTo(new BigDecimal("100")));
    }

    @Test
    @DisplayName("销售发货生成四行凭证并结转成本")
    void shouldPostShipmentVoucher() {
        stubOpenPeriod();
        stubAccounts();
        when(voucherRepository.nextVoucherNo("202611")).thenReturn("V-002");
        doAnswer(invocation -> {
            Voucher voucher = invocation.getArgument(0);
            voucher.setId(11L);
            return null;
        }).when(voucherRepository).insert(any(Voucher.class));
        AutoVoucherCommand command = command(true, "100");
        command.setCostAmount(new BigDecimal("60"));

        Long voucherId = service.postShipment(command);

        assertEquals(11L, voucherId);
        ArgumentCaptor<Voucher> captor = ArgumentCaptor.forClass(Voucher.class);
        verify(voucherRepository).insert(captor.capture());
        Voucher voucher = captor.getValue();
        assertEquals(4, voucher.getLines().size());
        assertEquals(0, voucher.getTotalDebit().compareTo(new BigDecimal("160")));
        assertEquals(0, voucher.getTotalCredit().compareTo(new BigDecimal("160")));
        verify(voucherRepository).update(any(Voucher.class));
    }

    @Test
    @DisplayName("同一来源单据重复触发返回原凭证，不重复生成")
    void shouldBeIdempotent() {
        Voucher existing = new Voucher();
        existing.setId(8L);
        when(voucherRepository.findBySource("GOODS_RECEIPT", "GR-001")).thenReturn(Optional.of(existing));

        Long voucherId = service.postReceipt(command(false, "100"));

        assertEquals(8L, voucherId);
        verify(voucherRepository, never()).insert(any(Voucher.class));
    }

    @Test
    @DisplayName("期间已关账时自动记账拒绝")
    void shouldRejectClosedPeriod() {
        AccountingPeriod period = period();
        period.setStatus(MasterDataStatus.PERIOD_CLOSED);
        when(periodRepository.findByCode("202611")).thenReturn(Optional.of(period));

        BizException exception = assertThrows(BizException.class,
                () -> service.postReceipt(command(false, "100")));

        assertEquals(ErpErrorCode.PERIOD_CLOSED, exception.getCode());
    }

    @Test
    @DisplayName("科目不存在时自动记账拒绝")
    void shouldRejectMissingAccount() {
        stubOpenPeriod();
        when(accountRepository.findByCode(anyString())).thenReturn(Optional.empty());

        BizException exception = assertThrows(BizException.class,
                () -> service.postReceipt(command(false, "100")));

        assertEquals(ErpErrorCode.ACCOUNT_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("金额为空或零时拒绝自动记账")
    void shouldRejectZeroAmount() {
        stubOpenPeriod();
        stubAccounts();

        BizException exception = assertThrows(BizException.class,
                () -> service.postReceipt(command(false, "0")));

        assertEquals(ErpErrorCode.AMOUNT_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("库存盘盈生成借存货贷待处理财产损溢凭证")
    void shouldPostStockGainVoucher() {
        stubOpenPeriod();
        stubAccounts();
        when(voucherRepository.nextVoucherNo("202611")).thenReturn("V-003");
        doAnswer(invocation -> {
            Voucher voucher = invocation.getArgument(0);
            voucher.setId(12L);
            return null;
        }).when(voucherRepository).insert(any(Voucher.class));

        Long voucherId = service.postStockVariance(stockCommand("10"));

        assertEquals(12L, voucherId);
        ArgumentCaptor<Voucher> captor = ArgumentCaptor.forClass(Voucher.class);
        verify(voucherRepository).insert(captor.capture());
        Voucher voucher = captor.getValue();
        assertEquals(2, voucher.getLines().size());
        assertEquals(FinanceStatus.VOUCHER_POSTED, voucher.getStatus());
        assertEquals("STOCKTAKE", voucher.getSourceType());
        assertEquals(0, voucher.getTotalDebit().compareTo(new BigDecimal("10")));
        assertEquals(0, voucher.getTotalCredit().compareTo(new BigDecimal("10")));
        verify(voucherRepository).update(any(Voucher.class));
    }

    @Test
    @DisplayName("库存盘亏生成借待处理财产损溢贷存货凭证")
    void shouldPostStockLossVoucher() {
        stubOpenPeriod();
        stubAccounts();
        when(voucherRepository.nextVoucherNo("202611")).thenReturn("V-004");
        doAnswer(invocation -> {
            Voucher voucher = invocation.getArgument(0);
            voucher.setId(13L);
            return null;
        }).when(voucherRepository).insert(any(Voucher.class));

        Long voucherId = service.postStockVariance(stockCommand("-10"));

        assertEquals(13L, voucherId);
        ArgumentCaptor<Voucher> captor = ArgumentCaptor.forClass(Voucher.class);
        verify(voucherRepository).insert(captor.capture());
        Voucher voucher = captor.getValue();
        assertEquals(2, voucher.getLines().size());
        assertEquals(0, voucher.getTotalDebit().compareTo(new BigDecimal("10")));
        assertEquals(0, voucher.getTotalCredit().compareTo(new BigDecimal("10")));
    }

    @Test
    @DisplayName("库存差异为零时不生成凭证")
    void shouldSkipZeroStockVariance() {
        Long voucherId = service.postStockVariance(stockCommand("0"));

        assertNull(voucherId);
        verify(voucherRepository, never()).insert(any(Voucher.class));
    }

    @Test
    @DisplayName("生产领料生成借生产成本贷存货凭证")
    void shouldPostProductionIssueVoucher() {
        stubOpenPeriod();
        stubAccounts();
        when(voucherRepository.nextVoucherNo("202611")).thenReturn("V-005");
        doAnswer(invocation -> {
            Voucher voucher = invocation.getArgument(0);
            voucher.setId(14L);
            return null;
        }).when(voucherRepository).insert(any(Voucher.class));

        Long voucherId = service.postProductionIssue(productionCommand("PRODUCTION_ISSUE", "MO-001", "100"));

        assertEquals(14L, voucherId);
        ArgumentCaptor<Voucher> captor = ArgumentCaptor.forClass(Voucher.class);
        verify(voucherRepository).insert(captor.capture());
        Voucher voucher = captor.getValue();
        assertEquals(2, voucher.getLines().size());
        assertEquals(FinanceStatus.VOUCHER_POSTED, voucher.getStatus());
        assertEquals(0, voucher.getTotalDebit().compareTo(new BigDecimal("100")));
        assertEquals(0, voucher.getTotalCredit().compareTo(new BigDecimal("100")));
        verify(voucherRepository).update(any(Voucher.class));
    }

    @Test
    @DisplayName("生产完工入库生成借存货贷生产成本凭证")
    void shouldPostProductionReceiptVoucher() {
        stubOpenPeriod();
        stubAccounts();
        when(voucherRepository.nextVoucherNo("202611")).thenReturn("V-006");
        doAnswer(invocation -> {
            Voucher voucher = invocation.getArgument(0);
            voucher.setId(15L);
            return null;
        }).when(voucherRepository).insert(any(Voucher.class));

        Long voucherId = service.postProductionReceipt(productionCommand("PRODUCTION_RECEIPT", "MO-001-C10", "100"));

        assertEquals(15L, voucherId);
        ArgumentCaptor<Voucher> captor = ArgumentCaptor.forClass(Voucher.class);
        verify(voucherRepository).insert(captor.capture());
        Voucher voucher = captor.getValue();
        assertEquals(2, voucher.getLines().size());
        assertEquals(0, voucher.getTotalDebit().compareTo(new BigDecimal("100")));
        assertEquals(0, voucher.getTotalCredit().compareTo(new BigDecimal("100")));
    }

    @Test
    @DisplayName("生产记账金额为零时不生成凭证")
    void shouldSkipZeroProductionVoucher() {
        Long voucherId = service.postProductionIssue(productionCommand("PRODUCTION_ISSUE", "MO-001", "0"));

        assertNull(voucherId);
        verify(voucherRepository, never()).insert(any(Voucher.class));
    }

    @Test
    @DisplayName("薪资计提生成借管理费用贷应付职工薪酬凭证")
    void shouldPostPayrollAccrualVoucher() {
        stubOpenPeriod();
        stubPayrollAccounts();
        when(voucherRepository.nextVoucherNo("202611")).thenReturn("V-007");
        doAnswer(invocation -> {
            Voucher voucher = invocation.getArgument(0);
            voucher.setId(16L);
            return null;
        }).when(voucherRepository).insert(any(Voucher.class));

        Long voucherId = service.postPayrollAccrual(payrollCommand("PAYROLL_ACCRUAL"));

        assertEquals(16L, voucherId);
        ArgumentCaptor<Voucher> captor = ArgumentCaptor.forClass(Voucher.class);
        verify(voucherRepository).insert(captor.capture());
        Voucher voucher = captor.getValue();
        assertEquals(2, voucher.getLines().size());
        assertEquals(FinanceStatus.VOUCHER_POSTED, voucher.getStatus());
        assertEquals(0, voucher.getTotalDebit().compareTo(new BigDecimal("11000")));
        assertEquals(0, voucher.getTotalCredit().compareTo(new BigDecimal("11000")));
        verify(voucherRepository).update(any(Voucher.class));
    }

    @Test
    @DisplayName("薪资发放生成借应付职工薪酬贷存款税费其他应付款凭证")
    void shouldPostPayrollPaymentVoucher() {
        stubOpenPeriod();
        stubPayrollAccounts();
        when(voucherRepository.nextVoucherNo("202611")).thenReturn("V-008");
        doAnswer(invocation -> {
            Voucher voucher = invocation.getArgument(0);
            voucher.setId(17L);
            return null;
        }).when(voucherRepository).insert(any(Voucher.class));

        Long voucherId = service.postPayrollPayment(payrollCommand("PAYROLL_PAYMENT"));

        assertEquals(17L, voucherId);
        ArgumentCaptor<Voucher> captor = ArgumentCaptor.forClass(Voucher.class);
        verify(voucherRepository).insert(captor.capture());
        Voucher voucher = captor.getValue();
        assertEquals(4, voucher.getLines().size());
        assertEquals(0, voucher.getTotalDebit().compareTo(new BigDecimal("11000")));
        assertEquals(0, voucher.getTotalCredit().compareTo(new BigDecimal("11000")));
        verify(voucherRepository).update(any(Voucher.class));
    }

    @Test
    @DisplayName("薪资发放金额为零时不生成凭证")
    void shouldSkipZeroPayrollVoucher() {
        AutoVoucherCommand command = payrollCommand("PAYROLL_ACCRUAL");
        command.setAmount(BigDecimal.ZERO);

        Long voucherId = service.postPayrollAccrual(command);

        assertNull(voucherId);
        verify(voucherRepository, never()).insert(any(Voucher.class));
    }

    /**
     * 构造薪资记账命令。
     *
     * @param sourceType 来源类型
     * @return 命令
     */
    private AutoVoucherCommand payrollCommand(String sourceType) {
        AutoVoucherCommand command = new AutoVoucherCommand();
        command.setSourceType(sourceType);
        command.setSourceId("PR-202611-0001");
        command.setVoucherDate(LocalDate.of(2026, 11, 30));
        command.setAmount(new BigDecimal("11000"));
        command.setNetAmount(new BigDecimal("9035"));
        command.setTaxAmount(new BigDecimal("215"));
        command.setWithholdingAmount(new BigDecimal("1750"));
        command.setOperatorId(99L);
        return command;
    }

    /**
     * 打桩薪资记账涉及的科目。
     */
    private void stubPayrollAccounts() {
        when(accountRepository.findByCode("6602")).thenReturn(Optional.of(account("6602", "管理费用")));
        when(accountRepository.findByCode("2211")).thenReturn(Optional.of(account("2211", "应付职工薪酬")));
        when(accountRepository.findByCode("1002")).thenReturn(Optional.of(account("1002", "银行存款")));
        when(accountRepository.findByCode("2221")).thenReturn(Optional.of(account("2221", "应交税费")));
        when(accountRepository.findByCode("2241")).thenReturn(Optional.of(account("2241", "其他应付款")));
    }

    /**
     * 构造生产记账命令。
     *
     * @param sourceType 来源类型
     * @param sourceId 来源单据号
     * @param amount 金额
     * @return 命令
     */
    private AutoVoucherCommand productionCommand(String sourceType, String sourceId, String amount) {
        AutoVoucherCommand command = new AutoVoucherCommand();
        command.setSourceType(sourceType);
        command.setSourceId(sourceId);
        command.setVoucherDate(LocalDate.of(2026, 11, 4));
        command.setCurrency("CNY");
        command.setAmount(new BigDecimal(amount));
        command.setOperatorId(99L);
        return command;
    }

    /**
     * 构造库存差异记账命令。
     *
     * @param amount 差异金额（带符号）
     * @return 命令
     */
    private AutoVoucherCommand stockCommand(String amount) {
        AutoVoucherCommand command = new AutoVoucherCommand();
        command.setSourceType("STOCKTAKE");
        command.setSourceId("ST-001");
        command.setVoucherDate(LocalDate.of(2026, 11, 4));
        command.setCurrency("CNY");
        command.setAmount(new BigDecimal(amount));
        command.setOperatorId(99L);
        return command;
    }

    /**
     * 构造记账命令。
     *
     * @param shipment 是否发货
     * @param amount 金额
     * @return 命令
     */
    private AutoVoucherCommand command(boolean shipment, String amount) {
        AutoVoucherCommand command = new AutoVoucherCommand();
        command.setSourceType(shipment ? "SHIPMENT" : "GOODS_RECEIPT");
        command.setSourceId(shipment ? "DN-001" : "GR-001");
        command.setVoucherDate(LocalDate.of(2026, 11, 4));
        command.setCurrency("CNY");
        command.setAmount(new BigDecimal(amount));
        command.setOperatorId(99L);
        return command;
    }

    /**
     * 打桩打开的会计期间。
     */
    private void stubOpenPeriod() {
        when(periodRepository.findByCode("202611")).thenReturn(Optional.of(period()));
    }

    /**
     * 打桩自动记账涉及的科目。
     */
    private void stubAccounts() {
        when(accountRepository.findByCode("1405")).thenReturn(Optional.of(account("1405", "库存商品")));
        when(accountRepository.findByCode("2202")).thenReturn(Optional.of(account("2202", "应付账款")));
        when(accountRepository.findByCode("1122")).thenReturn(Optional.of(account("1122", "应收账款")));
        when(accountRepository.findByCode("6001")).thenReturn(Optional.of(account("6001", "主营业务收入")));
        when(accountRepository.findByCode("6401")).thenReturn(Optional.of(account("6401", "主营业务成本")));
        when(accountRepository.findByCode("1901")).thenReturn(Optional.of(account("1901", "待处理财产损溢")));
        when(accountRepository.findByCode("5001")).thenReturn(Optional.of(account("5001", "生产成本")));
    }

    /**
     * 构造打开的会计期间。
     *
     * @return 期间
     */
    private AccountingPeriod period() {
        AccountingPeriod period = new AccountingPeriod();
        period.setPeriodCode("202611");
        period.setStartDate(LocalDate.of(2026, 11, 1));
        period.setEndDate(LocalDate.of(2026, 11, 30));
        period.setStatus(MasterDataStatus.PERIOD_OPEN);
        return period;
    }

    /**
     * 构造可记账科目。
     *
     * @param code 编码
     * @param name 名称
     * @return 科目
     */
    private Account account(String code, String name) {
        Account account = new Account();
        account.setCode(code);
        account.setName(name);
        account.setLeaf(true);
        account.setStatus(MasterDataStatus.ENABLED);
        return account;
    }
}
