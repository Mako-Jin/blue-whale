package com.bluewhale.mall.coupon.domain.model;

/**
 * 用户券可用性判定结果。
 *
 * <p>返回原因而不是布尔值，是因为「为什么这张券不能用」要显示给买家：
 * 只回 true/false，前端只能把不可用的券藏起来，用户看着券包里的券
 * 却在下单页找不到，客服每天都要解释一遍。</p>
 *
 * @author BlueWhale
 */
public enum CouponUsability {

    /** 可用 */
    USABLE("可用"),

    /** 已使用 */
    USED("已使用"),

    /** 已过期 */
    EXPIRED("已过期"),

    /** 不适用于当前渠道 */
    CHANNEL_MISMATCH("不适用于该商城"),

    /** 订单中没有券适用类目的商品（品类券） */
    CATEGORY_NOT_MATCHED("订单中没有适用类目的商品"),

    /** 订单中没有券适用商品的商品（商品券） */
    SKU_NOT_MATCHED("订单中没有适用商品"),

    /** 未达到使用门槛 */
    THRESHOLD_NOT_MET("订单金额未达门槛");

    /** 面向买家的说明文案 */
    private final String label;

    /**
     * 构造可用性枚举。
     *
     * @param label 面向买家的说明文案
     */
    CouponUsability(String label) {
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
     * 是否可用。
     *
     * @return 可用返回 true
     */
    public boolean usable() {
        return this == USABLE;
    }
}
