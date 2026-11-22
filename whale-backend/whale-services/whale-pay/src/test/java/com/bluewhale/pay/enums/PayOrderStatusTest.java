package com.bluewhale.pay.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PayOrderStatus} 状态机单元测试：合法流转与终态封闭性。
 *
 * @author BlueWhale
 */
class PayOrderStatusTest {

    @Test
    @DisplayName("CREATED 可流转到 PAYING / PAID / CANCELLED")
    void createdShouldAllowPayAndCancel() {
        assertTrue(PayOrderStatus.CREATED.canTransitionTo(PayOrderStatus.PAYING));
        assertTrue(PayOrderStatus.CREATED.canTransitionTo(PayOrderStatus.PAID));
        assertTrue(PayOrderStatus.CREATED.canTransitionTo(PayOrderStatus.CANCELLED));
    }

    @Test
    @DisplayName("PAYING 允许落账与渠道失败回退")
    void payingShouldAllowSettleAndRelease() {
        assertTrue(PayOrderStatus.PAYING.canTransitionTo(PayOrderStatus.PAID));
        assertTrue(PayOrderStatus.PAYING.canTransitionTo(PayOrderStatus.CREATED));
        assertFalse(PayOrderStatus.PAYING.canTransitionTo(PayOrderStatus.CANCELLED));
    }

    @Test
    @DisplayName("PAID 只能流转到 REFUNDED")
    void paidShouldOnlyAllowRefund() {
        assertTrue(PayOrderStatus.PAID.canTransitionTo(PayOrderStatus.REFUNDED));
        assertFalse(PayOrderStatus.PAID.canTransitionTo(PayOrderStatus.PAID));
        assertFalse(PayOrderStatus.PAID.canTransitionTo(PayOrderStatus.CANCELLED));
    }

    @Test
    @DisplayName("终态不允许任何流转")
    void terminalStatesShouldBeClosed() {
        assertTrue(PayOrderStatus.CANCELLED.allowedTransitions().isEmpty());
        assertTrue(PayOrderStatus.REFUNDED.allowedTransitions().isEmpty());
        assertFalse(PayOrderStatus.REFUNDED.canTransitionTo(PayOrderStatus.PAID));
    }
}
