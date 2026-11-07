package com.bluewhale.mall.coupon.application.view;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户券视图（我的券与下单可用券共用）。
 *
 * <p>{@code discountAmount} 与 {@code usability} 只在带订单金额查询时才有值：
 * 券包列表没有订单上下文，算不出「能减多少」，返回 null 比返回 0 诚实。</p>
 *
 * @author BlueWhale
 */
@Data
public class CouponUserView {

    /** 用户券 ID（下单时传它，不是模板 ID） */
    private Long id;

    /** 券模板 ID */
    private Long couponId;

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

    /** 适用渠道（空 = 全场） */
    private Long channelId;

    /** 生效时间 */
    private LocalDateTime validFrom;

    /** 失效时间 */
    private LocalDateTime validTo;

    /** 状态 */
    private String status;

    /** 核销订单号 */
    private String orderNo;

    /** 核销时间 */
    private LocalDateTime usedTime;

    /** 本单可减金额（仅带订单金额查询时有值） */
    private BigDecimal discountAmount;

    /** 可用性编码（仅带订单金额查询时有值） */
    private String usability;

    /** 可用性说明（不可用时给买家的原因） */
    private String usabilityLabel;
}
