package com.bluewhale.mall.coupon.domain.model;

import java.util.Locale;

/**
 * 优惠券模板状态。
 *
 * @author BlueWhale
 */
public enum CouponStatus {

    /** 启用（可领取、可使用） */
    ACTIVE,

    /** 停用（不再发放；已领出去的券按快照判定，不受影响） */
    DISABLED;

    /**
     * 解析状态文本。
     *
     * @param value 文本
     * @return 状态；无法识别时回落到 {@link #ACTIVE}
     */
    public static CouponStatus parse(String value) {
        if (value == null || value.isBlank()) {
            return ACTIVE;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return ACTIVE;
        }
    }
}
