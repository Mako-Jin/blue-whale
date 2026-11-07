package com.bluewhale.mall.coupon.domain.model;

import com.bluewhale.mall.common.MallErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 用户券（领取实例）。
 *
 * <p>面额、门槛、折扣、渠道、有效期全部是<b>领取当时的快照</b>，不靠
 * {@code couponId} 反查模板。运营改模板时已经领出去的券不会跟着变——
 * 那是发出去的承诺。</p>
 *
 * <p>核销只认 {@link #markUsed}，回退只认 {@link #release}：直接 {@code setStatus}
 * 会让「已使用」这个状态失去唯一的写入点，日后没人能说清一张券到底被谁核销过。</p>
 *
 * @author BlueWhale
 */
@Data
public class CouponUser {

    /** 主键 */
    private Long id;

    /** 券模板 ID */
    private Long couponId;

    /** 持有人用户 ID */
    private Long userId;

    /** 该用户领取的第几张 */
    private Integer claimSeq;

    /** 券编码（快照） */
    private String couponCode;

    /** 券名称（快照） */
    private String couponName;

    /** 券类型（快照） */
    private CouponType couponType;

    /** 使用门槛（快照） */
    private BigDecimal thresholdAmount;

    /** 满减面额（快照） */
    private BigDecimal faceAmount;

    /** 折扣率（快照） */
    private BigDecimal discountRate;

    /** 折扣封顶（快照） */
    private BigDecimal maxDiscountAmount;

    /** 适用渠道（快照，空 = 全场） */
    private Long channelId;

    /** 生效时间（快照） */
    private LocalDateTime validFrom;

    /** 失效时间（快照） */
    private LocalDateTime validTo;

    /** 状态 */
    private CouponUserStatus status;

    /** 核销订单号 */
    private String orderNo;

    /** 核销时间 */
    private LocalDateTime usedTime;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 按模板快照签发一张用户券。
     *
     * @param template 券模板
     * @param userId   持有人
     * @param claimSeq 该用户领取的第几张（1 起）
     * @return 用户券
     */
    public static CouponUser issue(Coupon template, Long userId, int claimSeq) {
        CouponUser coupon = new CouponUser();
        coupon.setCouponId(template.getId());
        coupon.setUserId(userId);
        coupon.setClaimSeq(claimSeq);
        coupon.setCouponCode(template.getCouponCode());
        coupon.setCouponName(template.getCouponName());
        coupon.setCouponType(template.getCouponType());
        coupon.setThresholdAmount(template.getThresholdAmount());
        coupon.setFaceAmount(template.getFaceAmount());
        coupon.setDiscountRate(template.getDiscountRate());
        coupon.setMaxDiscountAmount(template.getMaxDiscountAmount());
        coupon.setChannelId(template.getChannelId());
        coupon.setValidFrom(template.getValidFrom());
        coupon.setValidTo(template.getValidTo());
        coupon.setStatus(CouponUserStatus.UNUSED);
        return coupon;
    }

    /**
     * 判定可用性。
     *
     * @param orderChannelId 订单渠道 ID
     * @param goodsAmount    商品总额（优惠前）
     * @param now            当前时间
     * @return 判定结果
     */
    public CouponUsability check(Long orderChannelId, BigDecimal goodsAmount, LocalDateTime now) {
        if (status == CouponUserStatus.USED) {
            return CouponUsability.USED;
        }
        if (expired(now)) {
            return CouponUsability.EXPIRED;
        }
        if (channelId != null && !Objects.equals(channelId, orderChannelId)) {
            return CouponUsability.CHANNEL_MISMATCH;
        }
        return rule().meetsThreshold(goodsAmount) ? CouponUsability.USABLE : CouponUsability.THRESHOLD_NOT_MET;
    }

    /**
     * 是否已过期。
     *
     * @param now 当前时间
     * @return 已过期返回 true
     */
    public boolean expired(LocalDateTime now) {
        if (status == CouponUserStatus.EXPIRED) {
            return true;
        }
        return validTo != null && now != null && now.isAfter(validTo);
    }

    /**
     * 是否还在生效期内（未开始的券不参与试算）。
     *
     * @param now 当前时间
     * @return 已生效返回 true
     */
    public boolean effective(LocalDateTime now) {
        return validFrom == null || now == null || !now.isBefore(validFrom);
    }

    /**
     * 计算优惠金额。
     *
     * @param goodsAmount 商品总额
     * @return 优惠金额
     */
    public BigDecimal discountFor(BigDecimal goodsAmount) {
        return rule().discountFor(goodsAmount);
    }

    /**
     * 核销到订单。
     *
     * @param orderNo 订单号
     * @param now     核销时间
     */
    public void markUsed(String orderNo, LocalDateTime now) {
        if (status == CouponUserStatus.USED) {
            throw MallErrorCode.exception(MallErrorCode.COUPON_STATE_INVALID, "该优惠券已被使用");
        }
        if (expired(now)) {
            throw MallErrorCode.exception(MallErrorCode.COUPON_STATE_INVALID, "该优惠券已过期");
        }
        this.status = CouponUserStatus.USED;
        this.orderNo = orderNo;
        this.usedTime = now;
    }

    /**
     * 释放回未使用（订单取消或整单退款）。
     *
     * <p>只对已核销的券生效；未使用的券重复调用是空操作，
     * 避免「取消两次」把状态打回奇怪的值。</p>
     */
    public void release() {
        if (status != CouponUserStatus.USED) {
            return;
        }
        this.status = CouponUserStatus.UNUSED;
        this.orderNo = null;
        this.usedTime = null;
    }

    /**
     * 计算规则快照。
     *
     * @return 计算规则
     */
    public CouponRule rule() {
        return new CouponRule(couponType, thresholdAmount, faceAmount, discountRate, maxDiscountAmount);
    }
}
