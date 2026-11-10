package com.bluewhale.mall.coupon.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券模板实体，对应表 {@code mall_coupon}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_coupon")
public class CouponEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 券编码 */
    private String couponCode;

    /** 券名称 */
    private String couponName;

    /** 券类型 */
    private String couponType;

    /** 使用门槛 */
    private BigDecimal thresholdAmount;

    /** 满减面额 */
    private BigDecimal faceAmount;

    /** 折扣率 */
    private BigDecimal discountRate;

    /** 折扣封顶 */
    private BigDecimal maxDiscountAmount;

    /** 适用渠道 ID */
    private Long channelId;

    /** 适用范围：ALL 全场 / CHANNEL 单渠道 / CATEGORY 品类 */
    private String scope;

    /** 适用类目 ID（逗号分隔，仅 CATEGORY） */
    private String categoryIds;

    /** 适用 SKU ID（逗号分隔，仅 SKU 商品券） */
    private String skuIds;

    /** 发行量 */
    private Integer totalQty;

    /** 已发行数量 */
    private Integer issuedQty;

    /** 每人限领张数 */
    private Integer perUserLimit;

    /** 生效时间 */
    private LocalDateTime validFrom;

    /** 失效时间 */
    private LocalDateTime validTo;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;
}
