package com.bluewhale.mall.aftersale.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 售后单实体，对应表 {@code mall_after_sale}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_after_sale")
public class AfterSaleEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 售后单号 */
    private String afterSaleNo;

    /** 订单 ID */
    private Long orderId;

    /** 订单号 */
    private String orderNo;

    /** 买家用户 ID */
    private Long userId;

    /** 类型 */
    private String type;

    /** 状态 */
    private String status;

    /** 本次申请退款金额 */
    private BigDecimal refundAmount;

    /** 售后原因 */
    private String reason;

    /** 问题描述 */
    private String description;

    /** 凭证图片 */
    private String images;

    /** 审核人 ID */
    private Long auditUserId;

    /** 审核人名称 */
    private String auditUserName;

    /** 审核时间 */
    private LocalDateTime auditTime;

    /** 审核意见 */
    private String auditRemark;

    /** 支付侧退款流水号 */
    private String refundNo;

    /** 退款完成时间 */
    private LocalDateTime refundTime;

    /** 退回物流单号 */
    private String returnLogisticsNo;
}
