package com.bluewhale.mall.order.domain.model;

import com.bluewhale.mall.coupon.domain.model.CouponRule;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * 订单用券明细（一单多券）。
 *
 * <p>券的编码、名称、类型、适用范围全部落快照：券模板事后改名或改类目，
 * 历史订单上「这张券当年为什么减了 20」必须还能自己解释自己。
 * {@link #rule} 是<b>非持久化</b>的计价期字段：金额计算需要券规则，
 * 但规则可由面额/折扣率等快照字段随时重建，单独落一份只会引入两处真相。</p>
 *
 * @author BlueWhale
 */
@Data
public class OrderCoupon {

    /** 主键 */
    private Long id;

    /** 订单 ID */
    private Long orderId;

    /** 订单号（冗余，便于按单对账） */
    private String orderNo;

    /** 用户券 ID */
    private Long couponUserId;

    /** 券模板 ID */
    private Long couponId;

    /** 券编码（快照） */
    private String couponCode;

    /** 券名称（快照） */
    private String couponName;

    /** 券类型（快照） */
    private String couponType;

    /** 适用范围（快照）：ALL 全场 / CHANNEL 店铺（单渠道）/ CATEGORY 品类 / SKU 商品 */
    private String scope;

    /** 适用类目 ID（快照，逗号分隔，仅品类券） */
    private String categoryIds;

    /** 适用 SKU ID（快照，逗号分隔，仅商品券） */
    private String skuIds;

    /** 该券本单生效优惠额 */
    private BigDecimal discountAmount;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 券计算规则（非持久化，计价期由应用层从用户券快照传入） */
    private transient CouponRule rule;

    /**
     * 是否为品类券。
     *
     * @return 品类券返回 true
     */
    public boolean isCategoryScoped() {
        return "CATEGORY".equals(scope);
    }

    /**
     * 是否为商品券。
     *
     * @return 商品券返回 true
     */
    public boolean isSkuScoped() {
        return "SKU".equals(scope);
    }

    /**
     * 快照里的适用 SKU ID 集合。
     *
     * @return SKU ID 集合（非商品券或未绑定返回空）
     */
    public Set<Long> skuIdSet() {
        if (skuIds == null || skuIds.isBlank()) {
            return Set.of();
        }
        Set<Long> result = new HashSet<>();
        for (String part : skuIds.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(Long.valueOf(trimmed));
            }
        }
        return result;
    }

    /**
     * 快照里的适用类目 ID 集合。
     *
     * @return 类目 ID 集合（非品类券或未绑定返回空）
     */
    public Set<Long> categoryIdSet() {
        if (categoryIds == null || categoryIds.isBlank()) {
            return Set.of();
        }
        Set<Long> result = new HashSet<>();
        for (String part : categoryIds.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(Long.valueOf(trimmed));
            }
        }
        return result;
    }
}
