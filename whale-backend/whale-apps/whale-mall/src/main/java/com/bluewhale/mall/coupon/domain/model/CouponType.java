package com.bluewhale.mall.coupon.domain.model;

import java.util.Locale;

/**
 * 优惠券类型。
 *
 * <p>只保留两种能一眼算清的规则：满减（减固定面额）与折扣（按比例打折，必须封顶）。
 * 兑换券、包邮券、赠品券都要在下单计价之外挂新的入口，本切片不做。</p>
 *
 * @author BlueWhale
 */
public enum CouponType {

    /** 满减：满门槛减固定面额 */
    FULL_REDUCTION,

    /** 折扣：按折扣率打折，受封顶金额约束 */
    DISCOUNT;

    /**
     * 解析券类型文本。
     *
     * @param value 文本
     * @return 券类型；无法识别时返回 null（由调用方决定抛错还是忽略）
     */
    public static CouponType parse(String value) {
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
