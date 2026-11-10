package com.bluewhale.crm.analytics;

import com.bluewhale.crm.analytics.domain.service.MetricCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link MetricCalculator} 指标口径单元测试。
 *
 * @author BlueWhale
 */
class MetricCalculatorTest {

    /** 被测计算器 */
    private final MetricCalculator calculator = new MetricCalculator();

    @Test
    @DisplayName("转化率：下游数量 / 上游数量 × 100，保留两位小数")
    void shouldCalculateConversionRate() {
        assertEquals(new BigDecimal("30.00"), calculator.conversionRate(3, 10));
    }

    @Test
    @DisplayName("转化率：上游为 0 时返回 0，避免除零")
    void shouldReturnZeroWhenUpstreamIsZero() {
        assertEquals(new BigDecimal("0.00"), calculator.conversionRate(5, 0));
        assertEquals(new BigDecimal("0.00"), calculator.conversionRate(0, 0));
    }

    @Test
    @DisplayName("加权预测：商机金额 × 赢单概率 / 100")
    void shouldCalculateWeightedForecast() {
        assertEquals(new BigDecimal("300.00"),
                calculator.weightedForecast(new BigDecimal("1000"), new BigDecimal("30")));
    }

    @Test
    @DisplayName("加权预测：金额为空、概率为空或概率为 0 时返回 0")
    void shouldReturnZeroForInvalidWeightedForecast() {
        assertEquals(new BigDecimal("0.00"), calculator.weightedForecast(null, new BigDecimal("30")));
        assertEquals(new BigDecimal("0.00"), calculator.weightedForecast(new BigDecimal("1000"), null));
        assertEquals(new BigDecimal("0.00"), calculator.weightedForecast(new BigDecimal("1000"), BigDecimal.ZERO));
    }

    @Test
    @DisplayName("目标完成率：已达成 / 目标 × 100；目标为 0 时返回 0")
    void shouldCalculateTargetCompletion() {
        assertEquals(new BigDecimal("80.00"),
                calculator.targetCompletion(new BigDecimal("800"), new BigDecimal("1000")));
        assertEquals(new BigDecimal("0.00"), calculator.targetCompletion(new BigDecimal("800"), BigDecimal.ZERO));
        assertEquals(new BigDecimal("0.00"), calculator.targetCompletion(null, null));
    }
}
