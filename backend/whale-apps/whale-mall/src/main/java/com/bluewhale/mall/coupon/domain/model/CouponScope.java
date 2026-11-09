package com.bluewhale.mall.coupon.domain.model;

import java.util.Locale;

/**
 * 券的适用范围。
 *
 * <p>范围决定门槛判定的分母与优惠的分摊对象：全场/渠道（店铺）券看整单商品小计，
 * 优惠摊到全部行；品类券只看命中类目商品行的小计、商品券只看命中 SKU 行的小计，
 * 优惠也只摊到命中行——否则「品类满 100 减 20」会被整单其他商品凑单白嫖。</p>
 *
 * <p>「店铺券」即 {@link #CHANNEL} 单渠道券：mall 渠道就是店铺主体，不再单设枚举。</p>
 *
 * @author BlueWhale
 */
public enum CouponScope {

    /** 全场通用（不限定渠道与类目） */
    ALL("全场通用"),

    /** 店铺（单渠道）适用（绑定一个商城渠道，渠道即店铺主体） */
    CHANNEL("店铺（单渠道）适用"),

    /** 品类适用（绑定一组商品类目） */
    CATEGORY("品类适用"),

    /** 商品适用（绑定一组 SKU，门槛与优惠只看命中行） */
    SKU("商品适用");

    /** 面向运营的说明文案 */
    private final String label;

    /**
     * 构造适用范围枚举。
     *
     * @param label 面向运营的说明文案
     */
    CouponScope(String label) {
        this.label = label;
    }

    /**
     * 说明文案。
     *
     * @return 文案
     */
    public String label() {
        return label;
    }

    /**
     * 解析适用范围文本。
     *
     * @param value 文本
     * @return 适用范围；无法识别时返回 null（由调用方决定抛错还是回落）
     */
    public static CouponScope parse(String value) {
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
