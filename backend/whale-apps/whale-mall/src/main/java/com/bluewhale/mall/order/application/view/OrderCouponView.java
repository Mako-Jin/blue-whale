package com.bluewhale.mall.order.application.view;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单用券明细视图。
 *
 * @author BlueWhale
 */
@Data
public class OrderCouponView {

    /** 用户券 ID */
    private Long couponUserId;

    /** 券模板 ID */
    private Long couponId;

    /** 券编码 */
    private String couponCode;

    /** 券名称 */
    private String couponName;

    /** 券类型 */
    private String couponType;

    /** 适用范围：ALL 全场 / CHANNEL 单渠道 / CATEGORY 品类 */
    private String scope;

    /** 本单生效优惠额 */
    private BigDecimal discountAmount;
}
