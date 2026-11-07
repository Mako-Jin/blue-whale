package com.bluewhale.mall.common.enums;

import java.util.Locale;

/**
 * 结算方式（渠道级）。
 *
 * <p>M0 只打通 {@link #ONLINE}（在线支付）：下单落到待支付，由 {@code whale-pay} 回调置已支付。
 * {@link #CREDIT}（账期）与 {@link #POINTS}（积分扣减）在 M1 落地，枚举先占位——
 * 提前定义可以让订单表把结算方式当快照存下来，避免 M1 加字段时回填历史单。</p>
 *
 * @author BlueWhale
 */
public enum SettlementType {

    /** 在线支付（M0 唯一可用） */
    ONLINE,

    /** 账期结算（M1） */
    CREDIT,

    /** 积分扣减（M1） */
    POINTS;

    /**
     * 解析结算方式文本。
     *
     * @param value 文本
     * @return 结算方式；无法识别时返回 null
     */
    public static SettlementType parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
