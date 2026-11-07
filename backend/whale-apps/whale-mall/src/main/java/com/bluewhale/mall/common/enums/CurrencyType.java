package com.bluewhale.mall.common.enums;

import java.util.Locale;

/**
 * 金额口径。
 *
 * <p>福利商城的「钱」是积分，不是元。让两者共用一列 {@code amount} 并把口径挂到
 * {@link #CASH} / {@link #POINTS} 上，比给积分单单开一套字段更省事，也更不容易在报表里
 * 把「100 元」和「100 积分」加到一起——因为报表必须显式按口径分组。</p>
 *
 * @author BlueWhale
 */
public enum CurrencyType {

    /** 人民币（元） */
    CASH,

    /** 福利积分 */
    POINTS;

    /**
     * 解析口径文本。
     *
     * @param value 文本
     * @return 口径；无法识别时返回 {@link #CASH}
     */
    public static CurrencyType parse(String value) {
        if (value == null || value.isBlank()) {
            return CASH;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return CASH;
        }
    }
}
