package com.bluewhale.mall.order.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 商城订单用券明细持久化实体（一单多券）。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_order_coupon")
public class OrderCouponEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

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

    /** 适用范围（快照）：ALL 全场 / CHANNEL 单渠道 / CATEGORY 品类 */
    private String scope;

    /** 适用类目 ID（快照，逗号分隔，仅品类券） */
    private String categoryIds;

    /** 适用 SKU ID（快照，逗号分隔，仅商品券） */
    private String skuIds;

    /** 该券本单生效优惠额 */
    private BigDecimal discountAmount;
}
