package com.bluewhale.erp.finance.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.erp.common.ErpErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Voucher} 领域规则单元测试。
 *
 * @author BlueWhale
 */
class VoucherTest {

    @Test
    @DisplayName("借贷平衡时计算合计")
    void shouldSumWhenBalanced() {
        Voucher voucher = voucher(line("1001", "100.00", null), line("1002", null, "100.00"));

        voucher.validateAndSum();

        assertEquals(new BigDecimal("100.00"), voucher.getTotalDebit());
        assertEquals(new BigDecimal("100.00"), voucher.getTotalCredit());
    }

    @Test
    @DisplayName("借贷不平衡时拒绝")
    void shouldRejectUnbalanced() {
        Voucher voucher = voucher(line("1001", "100.00", null), line("1002", null, "90.00"));

        BizException exception = assertThrows(BizException.class, voucher::validateAndSum);

        assertEquals(ErpErrorCode.VOUCHER_UNBALANCED, exception.getCode());
    }

    @Test
    @DisplayName("同一分录不能同时有借贷金额")
    void shouldRejectDualSidedLine() {
        Voucher voucher = voucher(line("1001", "10.00", "10.00"));

        BizException exception = assertThrows(BizException.class, voucher::validateAndSum);

        assertEquals(ErpErrorCode.AMOUNT_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("空分录与全零金额均被拒绝")
    void shouldRejectEmptyOrZeroLines() {
        Voucher empty = voucher();
        Voucher zero = voucher(line("1001", null, null));

        BizException emptyException = assertThrows(BizException.class, empty::validateAndSum);
        BizException zeroException = assertThrows(BizException.class, zero::validateAndSum);

        assertEquals(ErpErrorCode.VOUCHER_LINE_EMPTY, emptyException.getCode());
        assertEquals(ErpErrorCode.AMOUNT_INVALID, zeroException.getCode());
    }

    @Test
    @DisplayName("草稿可以记账，重复记账被拒绝")
    void shouldPostOnlyOnce() {
        Voucher voucher = voucher(line("1001", "10.00", null), line("1002", null, "10.00"));
        voucher.setStatus(FinanceStatus.VOUCHER_DRAFT);

        voucher.post(7L, LocalDateTime.of(2026, 11, 3, 10, 0));

        assertEquals(FinanceStatus.VOUCHER_POSTED, voucher.getStatus());
        assertEquals(7L, voucher.getPostedBy());
        assertTrue(voucher.reversible());
        BizException exception = assertThrows(BizException.class,
                () -> voucher.post(8L, LocalDateTime.now()));
        assertEquals(ErpErrorCode.VOUCHER_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("只有已记账凭证可以红冲并标记")
    void shouldMarkReversedOnlyForPostedVoucher() {
        Voucher draft = voucher(line("1001", "10.00", null), line("1002", null, "10.00"));
        draft.setStatus(FinanceStatus.VOUCHER_DRAFT);
        assertThrows(BizException.class, () -> draft.markReversed(99L));

        Voucher posted = voucher(line("1001", "10.00", null), line("1002", null, "10.00"));
        posted.setStatus(FinanceStatus.VOUCHER_POSTED);
        assertTrue(posted.reversible());
        posted.markReversed(99L);

        assertEquals(FinanceStatus.VOUCHER_REVERSED, posted.getStatus());
        assertEquals(99L, posted.getReversedById());
        assertFalse(posted.reversible());
    }

    /**
     * 构造凭证。
     *
     * @param lines 分录
     * @return 凭证
     */
    private static Voucher voucher(VoucherLine... lines) {
        Voucher voucher = new Voucher();
        voucher.setVoucherDate(LocalDate.of(2026, 11, 3));
        List<VoucherLine> list = new ArrayList<>(List.of(lines));
        voucher.setLines(list);
        return voucher;
    }

    /**
     * 构造分录。
     *
     * @param accountCode 科目编码
     * @param debit 借方金额
     * @param credit 贷方金额
     * @return 分录
     */
    private static VoucherLine line(String accountCode, String debit, String credit) {
        VoucherLine line = new VoucherLine();
        line.setAccountCode(accountCode);
        line.setAccountName("科目-" + accountCode);
        line.setDebitAmount(debit == null ? null : new BigDecimal(debit));
        line.setCreditAmount(credit == null ? null : new BigDecimal(credit));
        return line;
    }
}
