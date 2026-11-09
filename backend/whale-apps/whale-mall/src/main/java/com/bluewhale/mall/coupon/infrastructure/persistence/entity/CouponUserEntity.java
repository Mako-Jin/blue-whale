package com.bluewhale.mall.coupon.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户券实体，对应表 {@code mall_coupon_user}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_coupon_user")
public class CouponUserEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

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
    private String couponType;

    /** 使用门槛（快照） */
    private BigDecimal thresholdAmount;

    /** 满减面额（快照） */
    private BigDecimal faceAmount;

    /** 折扣率（快照） */
    private BigDecimal discountRate;

    /** 折扣封顶（快照） */
    private BigDecimal maxDiscountAmount;

    /** 适用渠道（快照） */
    private Long channelId;

    /** 适用范围（快照）：ALL 全场 / CHANNEL 单渠道 / CATEGORY 品类 */
    private String scope;

    /** 适用类目 ID（快照，逗号分隔，仅 CATEGORY） */
    private String categoryIds;

    /** 生效时间（快照） */
    private LocalDateTime validFrom;

    /** 失效时间（快照） */
    private LocalDateTime validTo;

    /** 状态 */
    private String status;

    /** 核销订单号 */
    private String orderNo;

    /** 核销时间 */
    private LocalDateTime usedTime;
}
