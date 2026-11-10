package com.bluewhale.erp.common;

import java.math.BigDecimal;

/**
 * 数量工具：统一数量精度与正数/非负校验。
 *
 * <p>数量保留 4 位小数，允许业务按计量单位精度四舍五入后再入库。</p>
 *
 * @author BlueWhale
 */
public final class Quantities {

    /** 数量小数位 */
    public static final int SCALE = 4;

    /**
     * 私有构造器：工具类，禁止实例化。
     */
    private Quantities() {
    }

    /**
     * 判断数量是否为零。
     *
     * @param quantity 数量
     * @return 为空或零返回 true
     */
    public static boolean isZero(BigDecimal quantity) {
        return quantity == null || quantity.signum() == 0;
    }

    /**
     * 校验数量为正数且不超过 4 位小数。
     *
     * @param quantity 数量
     * @param field 字段名（用于提示）
     * @return 原数量
     */
    public static BigDecimal requirePositive(BigDecimal quantity, String field) {
        if (quantity == null || quantity.signum() <= 0) {
            throw ErpErrorCode.exception(ErpErrorCode.QUANTITY_INVALID, field + "必须大于 0");
        }
        if (quantity.stripTrailingZeros().scale() > SCALE) {
            throw ErpErrorCode.exception(ErpErrorCode.QUANTITY_INVALID, field + "最多保留 4 位小数");
        }
        return quantity;
    }

    /**
     * 校验数量为非负数，空值视为零。
     *
     * @param quantity 数量
     * @param field 字段名
     * @return 规范化后的数量
     */
    public static BigDecimal nonNegative(BigDecimal quantity, String field) {
        BigDecimal value = quantity == null ? BigDecimal.ZERO : quantity;
        if (value.signum() < 0) {
            throw ErpErrorCode.exception(ErpErrorCode.QUANTITY_INVALID, field + "不能为负数");
        }
        return value;
    }
}
