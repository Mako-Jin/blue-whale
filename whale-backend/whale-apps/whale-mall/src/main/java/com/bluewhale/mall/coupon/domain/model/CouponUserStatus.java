package com.bluewhale.mall.coupon.domain.model;

import java.util.Locale;

/**
 * 用户券状态。
 *
 * <p>只有三个状态，不含「已锁定」：下单与核销在同一个事务里完成，
 * 中间态留在库里只会在事务回滚后变成一张卡在锁定中的券。</p>
 *
 * @author BlueWhale
 */
public enum CouponUserStatus {

    /** 未使用 */
    UNUSED,

    /** 已使用（已核销到某笔订单） */
    USED,

    /** 已过期（惰性标记，查询时按 valid_to 判定） */
    EXPIRED;

    /**
     * 解析状态文本。
     *
     * @param value 文本
     * @return 状态；无法识别时回落到 {@link #UNUSED}
     */
    public static CouponUserStatus parse(String value) {
        if (value == null || value.isBlank()) {
            return UNUSED;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return UNUSED;
        }
    }
}
