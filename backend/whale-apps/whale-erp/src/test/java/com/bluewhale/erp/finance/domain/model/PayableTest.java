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
 * {@link Payable} 领域规则单元测试。
 *
 * @author BlueWhale
 */
class PayableTest {

    @Test
    @DisplayName("部分付款后状态为部分结清")
    void shouldPartiallySettle() {
        Payable payable = payable("800.00");

        payable.settle(new BigDecimal("200.00"));

        assertEquals(FinanceStatus.LEDGER_PARTIALLY_SETTLED, payable.getStatus());
        assertEquals(new BigDecimal("600.00"), payable.remaining());
    }

    @Test
    @DisplayName("全额付款后状态为已结清且不可继续付款")
    void shouldSettleFully() {
        Payable payable = payable("800.00");

        payable.settle(new BigDecimal("800.00"));

        assertEquals(FinanceStatus.LEDGER_SETTLED, payable.getStatus());
        BizException exception = assertThrows(BizException.class,
                () -> payable.settle(new BigDecimal("1.00")));
        assertEquals(ErpErrorCode.LEDGER_CLOSED, exception.getCode());
    }

    @Test
    @DisplayName("付款金额超过未结余额时拒绝")
    void shouldRejectOverSettlement() {
        Payable payable = payable("800.00");

        BizException exception = assertThrows(BizException.class,
                () -> payable.settle(new BigDecimal("900.00")));

        assertEquals(ErpErrorCode.SETTLE_EXCEEDED, exception.getCode());
    }

    @Test
    @DisplayName("逾期天数按到期日计算")
    void shouldComputeOverdueDays() {
        Payable payable = payable("800.00");

        assertEquals(4L, payable.overdueDays(LocalDate.of(2026, 11, 4)));
    }

    /**
     * 构造应付单。
     *
     * @param amount 应付金额
     * @return 应付单
     */
    private static Payable payable(String amount) {
        Payable payable = new Payable();
        payable.setDocNo("AP-001");
        payable.setSupplierId(9001L);
        payable.setSupplierName("示例供应商");
        payable.setDocDate(LocalDate.of(2026, 10, 1));
        payable.setDueDate(LocalDate.of(2026, 10, 31));
        payable.setCurrency("CNY");
        payable.setAmount(new BigDecimal(amount));
        payable.setSettledAmount(BigDecimal.ZERO);
        payable.setStatus(FinanceStatus.LEDGER_OPEN);
        return payable;
    }
}
