package com.bluewhale.crm.analytics.domain.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 指标计算器（纯 Java，无框架依赖）。
 *
 * <p>转化率 = 下游阶段数 / 上游阶段数 × 100；加权预测 = 商机金额 × 赢单概率 / 100；
 * 目标完成率 = 已达成金额 / 目标金额 × 100。分母为 0 或入参为空时统一返回 0，
 * 避免除零与空指针。</p>
 *
 * @author BlueWhale
 */
public class MetricCalculator {

    /** 百分比精度 */
    private static final int PERCENT_SCALE = 2;

    /**
     * 计算转化率。
     *
     * @param downstream 下游阶段数量
     * @param upstream 上游阶段数量
     * @return 百分比；上游为 0 返回 0
     */
    public BigDecimal conversionRate(long downstream, long upstream) {
        if (upstream <= 0) {
            return BigDecimal.ZERO.setScale(PERCENT_SCALE);
        }
        return BigDecimal.valueOf(downstream)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(upstream), PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 计算概率加权预测金额。
     *
     * @param amount 商机金额
     * @param probabilityPercent 赢单概率（0–100）
     * @return 加权金额；金额为空或概率为 0 返回 0
     */
    public BigDecimal weightedForecast(BigDecimal amount, BigDecimal probabilityPercent) {
        if (amount == null || amount.signum() <= 0 || probabilityPercent == null
                || probabilityPercent.signum() <= 0) {
            return BigDecimal.ZERO.setScale(PERCENT_SCALE);
        }
        BigDecimal probability = probabilityPercent.max(BigDecimal.ZERO).min(BigDecimal.valueOf(100));
        return amount.multiply(probability)
                .divide(BigDecimal.valueOf(100), PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 计算目标完成率。
     *
     * @param achieved 已达成金额
     * @param target 目标金额
     * @return 百分比；目标为空或为 0 返回 0
     */
    public BigDecimal targetCompletion(BigDecimal achieved, BigDecimal target) {
        if (target == null || target.signum() <= 0) {
            return BigDecimal.ZERO.setScale(PERCENT_SCALE);
        }
        BigDecimal value = achieved == null ? BigDecimal.ZERO : achieved;
        return value.multiply(BigDecimal.valueOf(100))
                .divide(target, PERCENT_SCALE, RoundingMode.HALF_UP);
    }
}
