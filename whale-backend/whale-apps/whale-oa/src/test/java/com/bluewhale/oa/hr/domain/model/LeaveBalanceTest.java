package com.bluewhale.oa.hr.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.oa.common.OaErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link LeaveBalance} 额度冻结 / 释放 / 扣减 / 调整规则单元测试。
 *
 * @author BlueWhale
 */
class LeaveBalanceTest {

    @Test
    @DisplayName("freeze：额度充足时累加冻结")
    void shouldFreezeWhenEnoughBalance() {
        LeaveBalance balance = balance("10.0", "2.0", "0.0");
        balance.freeze(new BigDecimal("3.0"), false);

        assertEquals(new BigDecimal("3.0"), balance.getFrozenDays());
        assertEquals(new BigDecimal("5.0"), balance.availableDays());
    }

    @Test
    @DisplayName("freeze：额度不足且不允许负额度时拒绝")
    void shouldRejectFreezeWhenInsufficient() {
        LeaveBalance balance = balance("10.0", "8.0", "1.0");

        BizException exception = assertThrows(BizException.class,
                () -> balance.freeze(new BigDecimal("2.0"), false));

        assertEquals(OaErrorCode.LEAVE_BALANCE_INSUFFICIENT, exception.getCode());
    }

    @Test
    @DisplayName("freeze：允许负额度时可超出总额度")
    void shouldAllowNegativeWhenConfigured() {
        LeaveBalance balance = balance("10.0", "10.0", "0.0");
        balance.freeze(new BigDecimal("2.0"), true);

        assertEquals(new BigDecimal("2.0"), balance.getFrozenDays());
        assertEquals(new BigDecimal("-2.0"), balance.availableDays());
    }

    @Test
    @DisplayName("freeze：非正数天数拒绝")
    void shouldRejectNonPositiveDays() {
        LeaveBalance balance = balance("10.0", "0.0", "0.0");

        BizException exception = assertThrows(BizException.class,
                () -> balance.freeze(BigDecimal.ZERO, false));

        assertEquals(OaErrorCode.LEAVE_BALANCE_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("release：释放不超过冻结")
    void shouldReleaseFrozenDays() {
        LeaveBalance balance = balance("10.0", "1.0", "3.0");
        balance.release(new BigDecimal("2.0"));

        assertEquals(new BigDecimal("1.0"), balance.getFrozenDays());
    }

    @Test
    @DisplayName("release：释放超过冻结时拒绝")
    void shouldRejectOverRelease() {
        LeaveBalance balance = balance("10.0", "1.0", "1.0");

        BizException exception = assertThrows(BizException.class,
                () -> balance.release(new BigDecimal("2.0")));

        assertEquals(OaErrorCode.LEAVE_BALANCE_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("deduct：先释放冻结再累加已使用")
    void shouldDeductFromFrozenToUsed() {
        LeaveBalance balance = balance("10.0", "1.0", "2.0");
        balance.deduct(new BigDecimal("2.0"));

        assertEquals(0, balance.getFrozenDays().compareTo(BigDecimal.ZERO));
        assertEquals(new BigDecimal("3.0"), balance.getUsedDays());
    }

    @Test
    @DisplayName("deduct：冻结不足时拒绝")
    void shouldRejectDeductWhenFrozenInsufficient() {
        LeaveBalance balance = balance("10.0", "1.0", "1.0");

        BizException exception = assertThrows(BizException.class,
                () -> balance.deduct(new BigDecimal("2.0")));

        assertEquals(OaErrorCode.LEAVE_BALANCE_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("adjust：总额度不小于已使用 + 冻结")
    void shouldAdjustTotalDays() {
        LeaveBalance balance = balance("10.0", "2.0", "1.0");
        balance.adjust(new BigDecimal("15.0"));

        assertEquals(new BigDecimal("15.0"), balance.getTotalDays());
        assertEquals(new BigDecimal("12.0"), balance.availableDays());
    }

    @Test
    @DisplayName("adjust：低于已使用 + 冻结时拒绝")
    void shouldRejectAdjustBelowOccupied() {
        LeaveBalance balance = balance("10.0", "2.0", "1.0");

        BizException exception = assertThrows(BizException.class,
                () -> balance.adjust(new BigDecimal("2.0")));

        assertEquals(OaErrorCode.LEAVE_BALANCE_INVALID, exception.getCode());
    }

    /**
     * 构造额度。
     *
     * @param total 总额度
     * @param used 已使用
     * @param frozen 冻结
     * @return 额度领域模型
     */
    private static LeaveBalance balance(String total, String used, String frozen) {
        LeaveBalance balance = new LeaveBalance();
        balance.setUserId(100L);
        balance.setLeaveTypeId(1L);
        balance.setBalanceYear(2026);
        balance.setTotalDays(new BigDecimal(total));
        balance.setUsedDays(new BigDecimal(used));
        balance.setFrozenDays(new BigDecimal(frozen));
        return balance;
    }
}
