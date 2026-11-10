package com.bluewhale.mall.coupon.domain.model;

import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 券的计算规则（门槛判定与优惠金额）。
 *
 * <p>抽成独立值对象的原因很实际：模板与用户券都要算优惠，模板上写一份、
 * 快照上再写一份，两处一旦算出差异，就会出现「试算 20 元、下单 18 元」
 * 这种没人能复现的客诉。计算逻辑只留一份。</p>
 *
 * <p>两个必须的兜底：折扣券按封顶金额截断（不封顶的 8 折券在十万级订单上
 * 会算出运营不敢认的优惠），且优惠金额不得超过商品总额（否则应付为负，
 * 支付侧会直接拒绝创建支付单）。</p>
 *
 * @author BlueWhale
 */
@Getter
public final class CouponRule {

    /** 券类型 */
    private final CouponType couponType;

    /** 使用门槛 */
    private final BigDecimal thresholdAmount;

    /** 满减面额 */
    private final BigDecimal faceAmount;

    /** 折扣率（0.85 表示 85 折） */
    private final BigDecimal discountRate;

    /** 折扣封顶金额 */
    private final BigDecimal maxDiscountAmount;

    /**
     * 构造计算规则。
     *
     * @param couponType        券类型
     * @param thresholdAmount   使用门槛
     * @param faceAmount        满减面额
     * @param discountRate      折扣率
     * @param maxDiscountAmount 折扣封顶
     */
    public CouponRule(CouponType couponType, BigDecimal thresholdAmount, BigDecimal faceAmount,
                      BigDecimal discountRate, BigDecimal maxDiscountAmount) {
        this.couponType = couponType;
        this.thresholdAmount = thresholdAmount;
        this.faceAmount = faceAmount;
        this.discountRate = discountRate;
        this.maxDiscountAmount = maxDiscountAmount;
    }

    /**
     * 是否为折扣券。
     *
     * @return 折扣券返回 true
     */
    public boolean isDiscount() {
        return couponType == CouponType.DISCOUNT;
    }

    /**
     * 是否达到使用门槛。
     *
     * @param goodsAmount 商品总额（优惠前）
     * @return 达到门槛返回 true
     */
    public boolean meetsThreshold(BigDecimal goodsAmount) {
        BigDecimal goods = goodsAmount == null ? BigDecimal.ZERO : goodsAmount;
        BigDecimal threshold = thresholdAmount == null ? BigDecimal.ZERO : thresholdAmount;
        return goods.compareTo(threshold) >= 0;
    }

    /**
     * 计算优惠金额。
     *
     * @param goodsAmount 商品总额（优惠前）
     * @return 优惠金额，0 到商品总额之间，两位小数
     */
    public BigDecimal discountFor(BigDecimal goodsAmount) {
        BigDecimal goods = goodsAmount == null ? BigDecimal.ZERO : goodsAmount;
        if (goods.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal discount = isDiscount() ? discountByRate(goods) : safe(faceAmount);
        discount = discount.setScale(2, RoundingMode.HALF_UP);
        if (discount.compareTo(goods) > 0) {
            discount = goods;
        }
        return discount.max(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
    }

    /**
     * 按折扣率计算并套用封顶。
     *
     * @param goods 商品总额
     * @return 优惠金额（未取整）
     */
    private BigDecimal discountByRate(BigDecimal goods) {
        BigDecimal rate = discountRate == null ? BigDecimal.ONE : discountRate;
        BigDecimal discount = goods.multiply(BigDecimal.ONE.subtract(rate));
        if (maxDiscountAmount != null && discount.compareTo(maxDiscountAmount) > 0) {
            return maxDiscountAmount;
        }
        return discount;
    }

    /**
     * 空值转 0。
     *
     * @param value 金额
     * @return 非空金额
     */
    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
