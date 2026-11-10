package com.bluewhale.erp.common;

import java.math.BigDecimal;
import java.util.Collection;

/**
 * 金额工具：统一精度与正数校验，避免各处重复实现。
 *
 * @author BlueWhale
 */
public final class Amounts {

    /** 金额小数位 */
    public static final int SCALE = 2;

    /**
     * 私有构造器：工具类，禁止实例化。
     */
    private Amounts() {
    }

    /**
     * 判断金额是否为零。
     *
     * @param amount 金额
     * @return 为空或零返回 true
     */
    public static boolean isZero(BigDecimal amount) {
        return amount == null || amount.signum() == 0;
    }

    /**
     * 校验金额为正数且不超过 2 位小数。
     *
     * @param amount 金额
     * @param field 字段名（用于提示）
     * @return 原金额
     */
    public static BigDecimal requirePositive(BigDecimal amount, String field) {
        if (amount == null || amount.signum() <= 0) {
            throw ErpErrorCode.exception(ErpErrorCode.AMOUNT_INVALID, field + "必须大于 0");
        }
        if (amount.stripTrailingZeros().scale() > SCALE) {
            throw ErpErrorCode.exception(ErpErrorCode.AMOUNT_INVALID, field + "最多保留 2 位小数");
        }
        return amount;
    }

    /**
     * 校验金额为非负数且不超过 2 位小数，空值视为零。
     *
     * @param amount 金额
     * @param field 字段名
     * @return 规范化后的金额
     */
    public static BigDecimal nonNegative(BigDecimal amount, String field) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        if (value.signum() < 0) {
            throw ErpErrorCode.exception(ErpErrorCode.AMOUNT_INVALID, field + "不能为负数");
        }
        if (value.stripTrailingZeros().scale() > SCALE) {
            throw ErpErrorCode.exception(ErpErrorCode.AMOUNT_INVALID, field + "最多保留 2 位小数");
        }
        return value;
    }

    /**
     * 汇总金额，空值按零处理。
     *
     * @param amounts 金额集合
     * @return 合计
     */
    public static BigDecimal total(Collection<BigDecimal> amounts) {
        return amounts.stream()
                .map(amount -> amount == null ? BigDecimal.ZERO : amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
