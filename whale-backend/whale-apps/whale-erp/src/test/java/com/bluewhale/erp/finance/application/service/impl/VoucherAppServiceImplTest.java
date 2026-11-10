package com.bluewhale.erp.finance.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.finance.application.command.VoucherCreateCommand;
import com.bluewhale.erp.finance.application.command.VoucherLineCommand;
import com.bluewhale.erp.finance.application.view.VoucherView;
import com.bluewhale.erp.finance.domain.model.FinanceStatus;
import com.bluewhale.erp.finance.domain.model.Voucher;
import com.bluewhale.erp.finance.domain.model.VoucherLine;
import com.bluewhale.erp.finance.domain.model.VoucherQuery;
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
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link VoucherAppServiceImpl} 单元测试。
 *
 * @author BlueWhale
 */
class VoucherAppServiceImplTest {

    /** 期间格式 */
    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    private final VoucherRepository voucherRepository = mock(VoucherRepository.class);

    private final AccountRepository accountRepository = mock(AccountRepository.class);

    private final AccountingPeriodRepository periodRepository = mock(AccountingPeriodRepository.class);

    private final VoucherAppServiceImpl service = new VoucherAppServiceImpl(
            voucherRepository, accountRepository, periodRepository);

    @Test
    @DisplayName("凭证分页与详情")
    void shouldPageAndDetailVoucher() {
        Voucher voucher = voucher("V202611-000001", FinanceStatus.VOUCHER_DRAFT, "1001", "1002");
        voucher.setId(5L);
        when(voucherRepository.page(any())).thenReturn(List.of(voucher));
        when(voucherRepository.count(any())).thenReturn(1L);
        when(voucherRepository.findById(5L)).thenReturn(Optional.of(voucher));

        PageResult<VoucherView> page = service.page(new VoucherQuery());
        VoucherView detail = service.detail(5L);

        assertEquals(1, page.getRecords().size());
        assertEquals(1L, page.getTotal());
        assertEquals("V202611-000001", detail.getVoucherNo());
        assertEquals(2, detail.getLines().size());
    }

    @Test
    @DisplayName("凭证不存在时详情抛出 NOT_FOUND")
    void shouldRejectMissingVoucherDetail() {
        when(voucherRepository.findById(99L)).thenReturn(Optional.empty());

        BizException exception = assertThrows(BizException.class, () -> service.detail(99L));

        assertEquals(ErpErrorCode.VOUCHER_NOT_FOUND, exception.getCode());
    }

    @Test
    @DisplayName("创建凭证成功并套用默认币种与汇率")
    void shouldCreateVoucher() {
        when(periodRepository.findByCode("202611")).thenReturn(Optional.of(openPeriod("202611")));
        when(accountRepository.findByCode("1001")).thenReturn(Optional.of(account("1001", "库存现金")));
        when(accountRepository.findByCode("1002")).thenReturn(Optional.of(account("1002", "银行存款")));
        when(voucherRepository.nextVoucherNo("202611")).thenReturn("V202611-000001");

        VoucherView view = service.create(command(LocalDate.of(2026, 11, 3),
                line("1001", "100.00", null), line("1002", null, "100.00")));

        assertEquals("CNY", view.getCurrency());
        assertEquals(BigDecimal.ONE, view.getExchangeRate());
        assertEquals(new BigDecimal("100.00"), view.getTotalDebit());
        assertEquals(new BigDecimal("100.00"), view.getTotalCredit());
        assertEquals("V202611-000001", view.getVoucherNo());
        assertEquals(FinanceStatus.VOUCHER_DRAFT, view.getStatus());
        verify(voucherRepository).insert(any(Voucher.class));
    }

    @Test
    @DisplayName("期间缺失或已关账时拒绝创建")
    void shouldRejectInvalidPeriod() {
        when(periodRepository.findByCode("202611")).thenReturn(Optional.empty());
        BizException missing = assertThrows(BizException.class, () -> service.create(
                command(LocalDate.of(2026, 11, 3), line("1001", "10.00", null), line("1002", null, "10.00"))));
        assertEquals(ErpErrorCode.VOUCHER_DATE_INVALID, missing.getCode());

        AccountingPeriod closed = openPeriod("202611");
        closed.setStatus(MasterDataStatus.PERIOD_CLOSED);
        when(periodRepository.findByCode("202611")).thenReturn(Optional.of(closed));
        BizException closedException = assertThrows(BizException.class, () -> service.create(
                command(LocalDate.of(2026, 11, 3), line("1001", "10.00", null), line("1002", null, "10.00"))));
        assertEquals(ErpErrorCode.PERIOD_CLOSED, closedException.getCode());
    }

    @Test
    @DisplayName("科目不存在或非末级时拒绝创建")
    void shouldRejectInvalidAccount() {
        when(periodRepository.findByCode("202611")).thenReturn(Optional.of(openPeriod("202611")));
        when(accountRepository.findByCode("1001")).thenReturn(Optional.empty());
        BizException missing = assertThrows(BizException.class, () -> service.create(
                command(LocalDate.of(2026, 11, 3), line("1001", "10.00", null), line("1002", null, "10.00"))));
        assertEquals(ErpErrorCode.ACCOUNT_INVALID, missing.getCode());

        Account parent = account("1001", "汇总科目");
        parent.setLeaf(Boolean.FALSE);
        when(accountRepository.findByCode("1001")).thenReturn(Optional.of(parent));
        BizException notPostable = assertThrows(BizException.class, () -> service.create(
                command(LocalDate.of(2026, 11, 3), line("1001", "10.00", null), line("1002", null, "10.00"))));
        assertEquals(ErpErrorCode.ACCOUNT_INVALID, notPostable.getCode());
    }

    @Test
    @DisplayName("借贷不平衡时拒绝创建")
    void shouldRejectUnbalancedVoucher() {
        when(periodRepository.findByCode("202611")).thenReturn(Optional.of(openPeriod("202611")));
        when(accountRepository.findByCode("1001")).thenReturn(Optional.of(account("1001", "库存现金")));
        when(accountRepository.findByCode("1002")).thenReturn(Optional.of(account("1002", "银行存款")));

        BizException exception = assertThrows(BizException.class, () -> service.create(
                command(LocalDate.of(2026, 11, 3), line("1001", "100.00", null), line("1002", null, "90.00"))));

        assertEquals(ErpErrorCode.VOUCHER_UNBALANCED, exception.getCode());
        verify(voucherRepository, never()).insert(any(Voucher.class));
    }

    @Test
    @DisplayName("记账成功且重复记账被拒绝")
    void shouldPostVoucher() {
        Voucher voucher = voucher("V202611-000001", FinanceStatus.VOUCHER_DRAFT, "1001", "1002");
        voucher.setId(5L);
        when(voucherRepository.findById(5L)).thenReturn(Optional.of(voucher));
        when(periodRepository.findByCode("202611")).thenReturn(Optional.of(openPeriod("202611")));

        VoucherView view = service.post(5L, 7L);

        assertEquals(FinanceStatus.VOUCHER_POSTED, view.getStatus());
        assertEquals(7L, view.getPostedBy());
        verify(voucherRepository).update(voucher);

        when(voucherRepository.findById(6L)).thenReturn(Optional.empty());
        BizException missing = assertThrows(BizException.class, () -> service.post(6L, 7L));
        assertEquals(ErpErrorCode.VOUCHER_NOT_FOUND, missing.getCode());
    }

    @Test
    @DisplayName("红冲生成反向凭证并标记原凭证")
    void shouldReversePostedVoucher() {
        Voucher original = voucher("V202611-000001", FinanceStatus.VOUCHER_POSTED, "1001", "1002");
        original.setId(5L);
        when(voucherRepository.findById(5L)).thenReturn(Optional.of(original));
        String currentPeriod = YearMonth.now().format(PERIOD_FORMATTER);
        when(periodRepository.findByCode(anyString())).thenReturn(Optional.of(openPeriod(currentPeriod)));
        when(voucherRepository.nextVoucherNo(currentPeriod)).thenReturn("V" + currentPeriod + "-000002");
        doAnswer(invocation -> {
            Voucher inserted = invocation.getArgument(0);
            if (FinanceStatus.TYPE_REVERSAL.equals(inserted.getVoucherType())) {
                inserted.setId(77L);
            }
            return null;
        }).when(voucherRepository).insert(any(Voucher.class));

        VoucherView reversal = service.reverse(5L, 8L);

        assertEquals(FinanceStatus.TYPE_REVERSAL, reversal.getVoucherType());
        assertEquals(5L, reversal.getReversalOfId());
        assertEquals(FinanceStatus.VOUCHER_POSTED, reversal.getStatus());
        assertEquals(new BigDecimal("100.00"), reversal.getTotalDebit());
        assertEquals("红冲：借方摘要", reversal.getLines().get(0).getSummary());
        assertEquals(FinanceStatus.VOUCHER_REVERSED, original.getStatus());
        assertEquals(77L, original.getReversedById());
        verify(voucherRepository, times(2)).update(any(Voucher.class));
    }

    @Test
    @DisplayName("非已记账凭证不允许红冲")
    void shouldRejectReverseDraft() {
        Voucher draft = voucher("V202611-000001", FinanceStatus.VOUCHER_DRAFT, "1001", "1002");
        draft.setId(5L);
        when(voucherRepository.findById(5L)).thenReturn(Optional.of(draft));

        BizException exception = assertThrows(BizException.class, () -> service.reverse(5L, 8L));

        assertEquals(ErpErrorCode.VOUCHER_STATE_CONFLICT, exception.getCode());

        when(voucherRepository.findById(9L)).thenReturn(Optional.empty());
        BizException missing = assertThrows(BizException.class, () -> service.reverse(9L, 8L));
        assertEquals(ErpErrorCode.VOUCHER_NOT_FOUND, missing.getCode());
    }

    /**
     * 构造凭证。
     *
     * @param voucherNo 凭证号
     * @param status 状态
     * @param debitCode 借方科目编码
     * @param creditCode 贷方科目编码
     * @return 凭证
     */
    private static Voucher voucher(String voucherNo, String status, String debitCode, String creditCode) {
        Voucher voucher = new Voucher();
        voucher.setVoucherNo(voucherNo);
        voucher.setVoucherDate(LocalDate.of(2026, 11, 3));
        voucher.setPeriodCode("202611");
        voucher.setVoucherType(FinanceStatus.TYPE_MANUAL);
        voucher.setStatus(status);
        voucher.setCurrency("CNY");
        voucher.setExchangeRate(BigDecimal.ONE);
        List<VoucherLine> lines = new ArrayList<>();
        lines.add(voucherLine(1, debitCode, "100.00", null, "借方摘要"));
        lines.add(voucherLine(2, creditCode, null, "100.00", "贷方摘要"));
        voucher.setLines(lines);
        voucher.setTotalDebit(new BigDecimal("100.00"));
        voucher.setTotalCredit(new BigDecimal("100.00"));
        return voucher;
    }

    /**
     * 构造分录。
     *
     * @param lineNo 行号
     * @param accountCode 科目编码
     * @param debit 借方
     * @param credit 贷方
     * @param summary 摘要
     * @return 分录
     */
    private static VoucherLine voucherLine(int lineNo, String accountCode, String debit, String credit,
                                           String summary) {
        VoucherLine line = new VoucherLine();
        line.setLineNo(lineNo);
        line.setAccountCode(accountCode);
        line.setAccountName("科目-" + accountCode);
        line.setDebitAmount(debit == null ? null : new BigDecimal(debit));
        line.setCreditAmount(credit == null ? null : new BigDecimal(credit));
        line.setSummary(summary);
        line.setCurrency("CNY");
        line.setExchangeRate(BigDecimal.ONE);
        return line;
    }

    /**
     * 构造打开期间。
     *
     * @param periodCode 期间编码
     * @return 期间
     */
    private static AccountingPeriod openPeriod(String periodCode) {
        AccountingPeriod period = new AccountingPeriod();
        period.setPeriodCode(periodCode);
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
    private static Account account(String code, String name) {
        Account account = new Account();
        account.setCode(code);
        account.setName(name);
        account.setAccountType("ASSET");
        account.setDirection("DEBIT");
        account.setLeaf(Boolean.TRUE);
        account.setStatus(MasterDataStatus.ENABLED);
        return account;
    }

    /**
     * 构造分录命令。
     *
     * @param code 科目编码
     * @param debit 借方
     * @param credit 贷方
     * @return 命令
     */
    private static VoucherLineCommand line(String code, String debit, String credit) {
        VoucherLineCommand command = new VoucherLineCommand();
        command.setAccountCode(code);
        command.setSummary("摘要-" + code);
        command.setDebitAmount(debit == null ? null : new BigDecimal(debit));
        command.setCreditAmount(credit == null ? null : new BigDecimal(credit));
        return command;
    }

    /**
     * 构造凭证创建命令。
     *
     * @param date 凭证日期
     * @param lines 分录
     * @return 命令
     */
    private static VoucherCreateCommand command(LocalDate date, VoucherLineCommand... lines) {
        VoucherCreateCommand command = new VoucherCreateCommand();
        command.setVoucherDate(date);
        command.setLines(List.of(lines));
        return command;
    }
}
