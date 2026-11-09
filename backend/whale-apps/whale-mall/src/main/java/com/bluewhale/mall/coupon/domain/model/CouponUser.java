package com.bluewhale.mall.coupon.domain.model;

import com.bluewhale.mall.common.MallErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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

    /** 适用渠道（快照，空 = 全场，仅 CHANNEL 用） */
    private Long channelId;

    /** 适用范围（快照） */
    private CouponScope scope;

    /** 适用类目 ID（快照，仅 CATEGORY 用） */
    private List<Long> categoryIds;

    /** 适用 SKU ID（快照，仅 SKU 商品券用） */
    private List<Long> skuIds;

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
        coupon.setScope(template.getScope() == null
                ? (template.getChannelId() == null ? CouponScope.ALL : CouponScope.CHANNEL) : template.getScope());
        coupon.setCategoryIds(template.getCategoryIds() == null ? null : List.copyOf(template.getCategoryIds()));
        coupon.setSkuIds(template.getSkuIds() == null ? null : List.copyOf(template.getSkuIds()));
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
        return checkForOrder(orderChannelId, null, goodsAmount, now);
    }

    /**
     * 按订单上下文判定可用性（含品类券口径）。
     *
     * <p>门槛分母由调用方按券的范围给：<b>全场/渠道券传整单商品小计，
     * 品类券传命中类目商品行的小计</b>。范围与分母在调用方分开决定，
     * 是因为「订单里有哪些类目」是订单域的事实，券域只认结论。
     * matchedGoods 为空表示调用方把门槛判定推迟到计价阶段
     * （品类券的分母依赖券自身的类目快照，取券之前算不出命中小计），此时不判门槛。</p>
     *
     * @param orderChannelId    订单渠道 ID
     * @param orderCategoryIds  订单商品类目 ID 集合（无上下文时为空）
     * @param matchedGoods      本券适用范围内的商品小计（门槛分母，可空 = 不判门槛）
     * @param now               当前时间
     * @return 判定结果
     */
    public CouponUsability checkForOrder(Long orderChannelId, Set<Long> orderCategoryIds,
                                         BigDecimal matchedGoods, LocalDateTime now) {
        return checkForOrder(orderChannelId, orderCategoryIds, null, matchedGoods, now);
    }

    /**
     * 按订单上下文判定可用性（含品类券与商品券口径）。
     *
     * <p>商品券（{@link CouponScope#SKU}）与品类券同构：门槛分母取命中 SKU
     * 行的小计（可空 = 不判门槛），适用性要求订单 SKU 集合与券快照的
     * {@code skuIds} 有交集，否则直接判不适用。</p>
     *
     * @param orderChannelId    订单渠道 ID
     * @param orderCategoryIds  订单商品类目 ID 集合（无上下文时为空）
     * @param orderSkuIds       订单商品 SKU ID 集合（无上下文时为空）
     * @param matchedGoods      本券适用范围内的商品小计（门槛分母，可空 = 不判门槛）
     * @param now               当前时间
     * @return 判定结果
     */
    public CouponUsability checkForOrder(Long orderChannelId, Set<Long> orderCategoryIds, Set<Long> orderSkuIds,
                                         BigDecimal matchedGoods, LocalDateTime now) {
        if (status == CouponUserStatus.USED) {
            return CouponUsability.USED;
        }
        if (expired(now)) {
            return CouponUsability.EXPIRED;
        }
        CouponScope effectiveScope = scope == null
                ? (channelId == null ? CouponScope.ALL : CouponScope.CHANNEL) : scope;
        CouponUsability scopeResult = checkScope(effectiveScope, orderChannelId, orderCategoryIds, orderSkuIds);
        if (scopeResult != null) {
            return scopeResult;
        }
        if (matchedGoods == null || rule().meetsThreshold(matchedGoods)) {
            return CouponUsability.USABLE;
        }
        return CouponUsability.THRESHOLD_NOT_MET;
    }

    /**
     * 按券范围判定订单命中情况（命中且无范围冲突返回 null，继续判门槛）。
     *
     * <p>channelId 非空时即使 scope 标了 ALL 也按受限处理：只能收紧不能放宽，
     * 否则 scope 与 channelId 不一致的老数据会绕过渠道校验。</p>
     *
     * @param effectiveScope   有效范围
     * @param orderChannelId   订单渠道 ID
     * @param orderCategoryIds 订单商品类目 ID 集合
     * @param orderSkuIds      订单商品 SKU ID 集合
     * @return 不适用的判定结果；命中返回 null
     */
    private CouponUsability checkScope(CouponScope effectiveScope, Long orderChannelId,
                                       Set<Long> orderCategoryIds, Set<Long> orderSkuIds) {
        if (effectiveScope != CouponScope.CATEGORY && effectiveScope != CouponScope.SKU && channelId != null
                && !Objects.equals(channelId, orderChannelId)) {
            return CouponUsability.CHANNEL_MISMATCH;
        }
        if (effectiveScope == CouponScope.CATEGORY
                && (orderCategoryIds == null || orderCategoryIds.isEmpty()
                || categoryIds == null || categoryIds.stream().noneMatch(orderCategoryIds::contains))) {
            return CouponUsability.CATEGORY_NOT_MATCHED;
        }
        if (effectiveScope == CouponScope.SKU
                && (orderSkuIds == null || orderSkuIds.isEmpty()
                || skuIds == null || skuIds.stream().noneMatch(orderSkuIds::contains))) {
            return CouponUsability.SKU_NOT_MATCHED;
        }
        return null;
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
