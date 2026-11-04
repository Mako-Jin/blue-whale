package com.bluewhale.erp.finance.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.finance.application.view.AccountBalanceView;
import com.bluewhale.erp.finance.application.view.FinancialReportView;
import com.bluewhale.erp.finance.application.view.LedgerView;
import com.bluewhale.erp.finance.application.view.ReportRowView;
import com.bluewhale.erp.finance.domain.model.VoucherLine;
import com.bluewhale.erp.finance.domain.repository.VoucherRepository;
import com.bluewhale.erp.masterdata.domain.model.Account;
import com.bluewhale.erp.masterdata.domain.model.AccountingPeriod;
import com.bluewhale.erp.masterdata.domain.model.MasterDataStatus;
import com.bluewhale.erp.masterdata.domain.repository.AccountRepository;
import com.bluewhale.erp.masterdata.domain.repository.AccountingPeriodRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link FinanceReportAppServiceImpl} 单元测试。
 *
 * @author BlueWhale
 */
class FinanceReportAppServiceImplTest {

    private final VoucherRepository voucherRepository = mock(VoucherRepository.class);

    private final AccountRepository accountRepository = mock(AccountRepository.class);

    private final AccountingPeriodRepository periodRepository = mock(AccountingPeriodRepository.class);

    private final FinanceReportAppServiceImpl service = new FinanceReportAppServiceImpl(
            voucherRepository, accountRepository, periodRepository);

    @Test
    @DisplayName("明细账计算期初、发生额与期末")
    void shouldBuildLedger() {
        Account account = account("1001", "库存现金", "ASSET", "DEBIT");
        when(accountRepository.findByCode("1001")).thenReturn(Optional.of(account));
        when(periodRepository.findByCode("202611")).thenReturn(Optional.of(period("202611")));
        when(periodRepository.findByCode("202612")).thenReturn(Optional.of(period("202612")));
        when(voucherRepository.listPostedLines("1001", null, "202610"))
                .thenReturn(List.of(line("1001", "100.00", null)));
        when(voucherRepository.listPostedLines("1001", "202611", "202612"))
                .thenReturn(List.of(line("1001", "50.00", null), line("1001", null, "20.00")));

        LedgerView ledger = service.ledger("1001", "202611", "202612");

        assertEquals(new BigDecimal("100.00"), ledger.getOpeningBalance());
        assertEquals(new BigDecimal("50.00"), ledger.getDebitTotal());
        assertEquals(new BigDecimal("20.00"), ledger.getCreditTotal());
        assertEquals(new BigDecimal("130.00"), ledger.getClosingBalance());
        assertEquals(2, ledger.getLines().size());
        assertEquals("库存现金", ledger.getAccountName());
    }

    @Test
    @DisplayName("期间非法、倒挂或科目缺失时拒绝查询明细账")
    void shouldRejectInvalidLedgerParameters() {
        when(accountRepository.findByCode("1001")).thenReturn(Optional.empty());
        BizException accountMissing = assertThrows(BizException.class,
                () -> service.ledger("1001", "202611", "202612"));
        assertEquals(ErpErrorCode.ACCOUNT_NOT_FOUND, accountMissing.getCode());

        when(accountRepository.findByCode("1001")).thenReturn(Optional.of(account("1001", "库存现金", "ASSET", "DEBIT")));
        when(periodRepository.findByCode("202611")).thenReturn(Optional.of(period("202611")));
        BizException badFormat = assertThrows(BizException.class,
                () -> service.ledger("1001", "2026-11", "202611"));
        assertEquals(ErpErrorCode.PERIOD_NOT_FOUND, badFormat.getCode());

        when(periodRepository.findByCode("202612")).thenReturn(Optional.empty());
        BizException missingPeriod = assertThrows(BizException.class,
                () -> service.ledger("1001", "202611", "202612"));
        assertEquals(ErpErrorCode.PERIOD_NOT_FOUND, missingPeriod.getCode());
    }

    @Test
    @DisplayName("起始期间晚于结束期间被拒绝")
    void shouldRejectReversedPeriodRange() {
        Account account = account("1001", "库存现金", "ASSET", "DEBIT");
        when(accountRepository.findByCode("1001")).thenReturn(Optional.of(account));
        when(periodRepository.findByCode("202611")).thenReturn(Optional.of(period("202611")));
        when(periodRepository.findByCode("202612")).thenReturn(Optional.of(period("202612")));

        BizException exception = assertThrows(BizException.class,
                () -> service.ledger("1001", "202612", "202611"));

        assertEquals(ErpErrorCode.PERIOD_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("科目余额表输出本期发生额与累计余额")
    void shouldBuildAccountBalances() {
        when(periodRepository.findByCode("202611")).thenReturn(Optional.of(period("202611")));
        when(accountRepository.listEnabled()).thenReturn(List.of(
                account("1001", "库存现金", "ASSET", "DEBIT"),
                account("2001", "应付账款", "LIABILITY", "CREDIT"),
                account("9999", "空科目", "ASSET", "DEBIT")));
        when(voucherRepository.listPostedLines(isNull(), isNull(), eq("202611")))
                .thenReturn(List.of(line("1001", "100.00", null), line("2001", null, "80.00")));
        when(voucherRepository.listPostedLines(isNull(), eq("202611"), eq("202611")))
                .thenReturn(List.of(line("1001", "50.00", null)));

        List<AccountBalanceView> rows = service.accountBalances("202611");

        assertEquals(2, rows.size());
        assertEquals("1001", rows.get(0).getAccountCode());
        assertEquals(new BigDecimal("50.00"), rows.get(0).getDebitTotal());
        assertEquals(new BigDecimal("100.00"), rows.get(0).getBalance());
        assertEquals(new BigDecimal("80.00"), rows.get(1).getBalance());
    }

    @Test
    @DisplayName("资产负债表按类型汇总并计入未结转利润")
    void shouldBuildBalanceSheet() {
        when(periodRepository.findByCode("202611")).thenReturn(Optional.of(period("202611")));
        when(accountRepository.listEnabled()).thenReturn(List.of(
                account("1001", "库存现金", "ASSET", "DEBIT"),
                account("2001", "应付账款", "LIABILITY", "CREDIT"),
                account("3001", "实收资本", "EQUITY", "CREDIT"),
                account("4001", "主营业务收入", "REVENUE", "CREDIT"),
                account("5001", "主营业务成本", "EXPENSE", "DEBIT")));
        when(voucherRepository.listPostedLines(isNull(), isNull(), eq("202611"))).thenReturn(List.of(
                line("1001", "100.00", null),
                line("2001", null, "100.00"),
                line("3001", null, "10.00"),
                line("4001", null, "500.00"),
                line("5001", "400.00", null)));

        FinancialReportView report = service.balanceSheet("202611");

        assertEquals("BALANCE_SHEET", report.getReportType());
        assertEquals(new BigDecimal("100.00"), report.getTotalAssets());
        assertEquals(new BigDecimal("100.00"), report.getTotalLiabilities());
        assertEquals(new BigDecimal("110.00"), report.getTotalEquity());
        assertEquals(new BigDecimal("100.00"), report.getNetProfit());
        assertEquals(4, report.getRows().size());
        ReportRowView profitRow = report.getRows().get(3);
        assertEquals("NET-PROFIT", profitRow.getAccountCode());
        assertEquals(new BigDecimal("100.00"), profitRow.getAmount());
    }

    @Test
    @DisplayName("利润表只取指定期间收入与费用")
    void shouldBuildIncomeStatement() {
        when(periodRepository.findByCode("202611")).thenReturn(Optional.of(period("202611")));
        when(accountRepository.listEnabled()).thenReturn(List.of(
                account("4001", "主营业务收入", "REVENUE", "CREDIT"),
                account("5001", "主营业务成本", "EXPENSE", "DEBIT")));
        when(voucherRepository.listPostedLines(isNull(), eq("202611"), eq("202611"))).thenReturn(List.of(
                line("4001", null, "500.00"),
                line("5001", "400.00", null)));

        FinancialReportView report = service.incomeStatement("202611");

        assertEquals("INCOME_STATEMENT", report.getReportType());
        assertEquals(new BigDecimal("500.00"), report.getTotalRevenue());
        assertEquals(new BigDecimal("400.00"), report.getTotalExpenses());
        assertEquals(new BigDecimal("100.00"), report.getNetProfit());
        assertEquals(2, report.getRows().size());
    }

    @Test
    @DisplayName("无发生额时报表为空且利润为零")
    void shouldReturnEmptyReport() {
        when(periodRepository.findByCode("202611")).thenReturn(Optional.of(period("202611")));
        when(accountRepository.listEnabled()).thenReturn(List.of(account("1001", "库存现金", "ASSET", "DEBIT")));
        when(voucherRepository.listPostedLines(isNull(), isNull(), eq("202611"))).thenReturn(List.of());

        FinancialReportView report = service.balanceSheet("202611");

        assertTrue(report.getRows().isEmpty());
        assertEquals(0, report.getTotalAssets().compareTo(BigDecimal.ZERO));
        assertEquals(0, report.getNetProfit().compareTo(BigDecimal.ZERO));
    }

    /**
     * 构造科目。
     *
     * @param code 编码
     * @param name 名称
     * @param type 类型
     * @param direction 方向
     * @return 科目
     */
    private static Account account(String code, String name, String type, String direction) {
        Account account = new Account();
        account.setCode(code);
        account.setName(name);
        account.setAccountType(type);
        account.setDirection(direction);
        account.setLeaf(Boolean.TRUE);
        account.setStatus(MasterDataStatus.ENABLED);
        return account;
    }

    /**
     * 构造期间。
     *
     * @param code 期间编码
     * @return 期间
     */
    private static AccountingPeriod period(String code) {
        AccountingPeriod period = new AccountingPeriod();
        period.setPeriodCode(code);
        period.setStartDate(LocalDate.of(2026, 11, 1));
        period.setEndDate(LocalDate.of(2026, 11, 30));
        period.setStatus(MasterDataStatus.PERIOD_OPEN);
        return period;
    }

    /**
     * 构造分录。
     *
     * @param code 科目编码
     * @param debit 借方
     * @param credit 贷方
     * @return 分录
     */
    private static VoucherLine line(String code, String debit, String credit) {
        VoucherLine line = new VoucherLine();
        line.setAccountCode(code);
        line.setAccountName("科目-" + code);
        line.setDebitAmount(debit == null ? BigDecimal.ZERO : new BigDecimal(debit));
        line.setCreditAmount(credit == null ? BigDecimal.ZERO : new BigDecimal(credit));
        return line;
    }
}
