package com.bluewhale.erp.finance.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.erp.common.ErpErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link Receivable} 领域规则单元测试。
 *
 * @author BlueWhale
 */
class ReceivableTest {

    @Test
    @DisplayName("部分核销后状态为部分结清")
    void shouldPartiallySettle() {
        Receivable receivable = receivable("1000.00", LocalDate.of(2026, 10, 31));

        receivable.settle(new BigDecimal("300.00"));

        assertEquals(FinanceStatus.LEDGER_PARTIALLY_SETTLED, receivable.getStatus());
        assertEquals(new BigDecimal("700.00"), receivable.remaining());
        assertEquals(new BigDecimal("300.00"), receivable.getSettledAmount());
    }

    @Test
    @DisplayName("全额核销后状态为已结清且不可继续核销")
    void shouldSettleFully() {
        Receivable receivable = receivable("1000.00", LocalDate.of(2026, 10, 31));

        receivable.settle(new BigDecimal("1000.00"));

        assertEquals(FinanceStatus.LEDGER_SETTLED, receivable.getStatus());
        assertEquals(0, receivable.remaining().compareTo(BigDecimal.ZERO));
        BizException exception = assertThrows(BizException.class,
                () -> receivable.settle(new BigDecimal("10.00")));
        assertEquals(ErpErrorCode.LEDGER_CLOSED, exception.getCode());
    }

    @Test
    @DisplayName("核销金额超过未结余额时拒绝")
    void shouldRejectOverSettlement() {
        Receivable receivable = receivable("1000.00", LocalDate.of(2026, 10, 31));

        BizException exception = assertThrows(BizException.class,
                () -> receivable.settle(new BigDecimal("1000.01")));

        assertEquals(ErpErrorCode.SETTLE_EXCEEDED, exception.getCode());
    }

    @Test
    @DisplayName("坏账计提全额后状态为已核销坏账")
    void shouldWriteOffWhenBadDebtCoversAll() {
        Receivable receivable = receivable("1000.00", LocalDate.of(2026, 10, 31));

        receivable.provisionBadDebt(new BigDecimal("1000.00"));

        assertEquals(FinanceStatus.LEDGER_WRITTEN_OFF, receivable.getStatus());
        assertEquals(0, receivable.remaining().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("坏账计提超过未结余额时拒绝")
    void shouldRejectOverBadDebt() {
        Receivable receivable = receivable("1000.00", LocalDate.of(2026, 10, 31));
        receivable.settle(new BigDecimal("400.00"));

        BizException exception = assertThrows(BizException.class,
                () -> receivable.provisionBadDebt(new BigDecimal("700.00")));

        assertEquals(ErpErrorCode.BAD_DEBT_EXCEEDED, exception.getCode());
    }

    @Test
    @DisplayName("逾期天数按到期日计算")
    void shouldComputeOverdueDays() {
        Receivable receivable = receivable("1000.00", LocalDate.of(2026, 10, 31));

        assertEquals(3L, receivable.overdueDays(LocalDate.of(2026, 11, 3)));
        assertEquals(-1L, receivable.overdueDays(LocalDate.of(2026, 10, 30)));
    }

    /**
     * 构造应收单。
     *
     * @param amount 应收金额
     * @param dueDate 到期日
     * @return 应收单
     */
    private static Receivable receivable(String amount, LocalDate dueDate) {
        Receivable receivable = new Receivable();
        receivable.setDocNo("AR-001");
        receivable.setCustomerId(7201L);
        receivable.setCustomerName("示例客户 A");
        receivable.setDocDate(dueDate.minusDays(30));
        receivable.setDueDate(dueDate);
        receivable.setCurrency("CNY");
        receivable.setAmount(new BigDecimal(amount));
        receivable.setSettledAmount(BigDecimal.ZERO);
        receivable.setBadDebtAmount(BigDecimal.ZERO);
        receivable.setStatus(FinanceStatus.LEDGER_OPEN);
        return receivable;
    }
}
