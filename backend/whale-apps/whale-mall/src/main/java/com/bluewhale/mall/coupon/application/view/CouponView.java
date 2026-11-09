package com.bluewhale.mall.coupon.application.view;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 优惠券模板视图。
 *
 * @author BlueWhale
 */
@Data
public class CouponView {

    /** 模板 ID */
    private Long id;

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

    /** 适用渠道 ID（仅 CHANNEL；空 = 全场） */
    private Long channelId;

    /** 适用范围：ALL 全场 / CHANNEL 店铺（单渠道）/ CATEGORY 品类 / SKU 商品 */
    private String scope;

    /** 适用类目 ID（仅 CATEGORY） */
    private List<Long> categoryIds;

    /** 适用 SKU ID（仅 SKU 商品券） */
    private List<Long> skuIds;

    /** 发行量（0 = 不限） */
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

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
