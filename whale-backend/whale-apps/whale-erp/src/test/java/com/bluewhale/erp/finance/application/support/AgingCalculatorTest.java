package com.bluewhale.erp.finance.application.support;

import com.bluewhale.erp.finance.application.view.AgingView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link AgingCalculator} 账龄分桶单元测试。
 *
 * @author BlueWhale
 */
class AgingCalculatorTest {

    @Test
    @DisplayName("未到期金额进入未到期桶")
    void shouldPutNotDue() {
        AgingView view = AgingCalculator.empty("AR", 7201L, "客户A");

        AgingCalculator.accumulate(view, amount("100.00"), -5L);
        AgingCalculator.accumulate(view, amount("50.00"), 0L);

        assertEquals(amount("150.00"), view.getTotal());
        assertEquals(amount("150.00"), view.getNotDue());
        assertEquals(BigDecimal.ZERO, view.getOverdue1To30());
    }

    @Test
    @DisplayName("逾期天数按 30/60/90 分桶")
    void shouldBucketByOverdueDays() {
        AgingView view = AgingCalculator.empty("AR", 7201L, "客户A");

        AgingCalculator.accumulate(view, amount("10.00"), 1L);
        AgingCalculator.accumulate(view, amount("10.00"), 30L);
        AgingCalculator.accumulate(view, amount("10.00"), 31L);
        AgingCalculator.accumulate(view, amount("10.00"), 60L);
        AgingCalculator.accumulate(view, amount("10.00"), 61L);
        AgingCalculator.accumulate(view, amount("10.00"), 90L);
        AgingCalculator.accumulate(view, amount("10.00"), 91L);

        assertEquals(amount("70.00"), view.getTotal());
        assertEquals(amount("20.00"), view.getOverdue1To30());
        assertEquals(amount("20.00"), view.getOverdue31To60());
        assertEquals(amount("20.00"), view.getOverdue61To90());
        assertEquals(amount("10.00"), view.getOverdueOver90());
    }

    @Test
    @DisplayName("空汇总行各金额初始化为零")
    void shouldInitializeZeros() {
        AgingView view = AgingCalculator.empty("AP", 9001L, "供应商A");

        assertEquals("AP", view.getDimension());
        assertEquals(9001L, view.getPartyId());
        assertEquals(BigDecimal.ZERO, view.getTotal());
        assertEquals(BigDecimal.ZERO, view.getOverdueOver90());
    }

    /**
     * 构造金额。
     *
     * @param value 金额文本
     * @return 金额
     */
    private static BigDecimal amount(String value) {
        return new BigDecimal(value);
    }
}
