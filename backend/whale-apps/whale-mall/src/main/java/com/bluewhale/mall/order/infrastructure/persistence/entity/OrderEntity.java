package com.bluewhale.mall.order.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体，对应表 {@code mall_order}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_order")
public class OrderEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 订单号 */
    private String orderNo;

    /** 渠道 ID */
    private Long channelId;

    /** 渠道编码（快照） */
    private String channelCode;

    /** 形态（快照） */
    private String mode;

    /** 结算方式（快照） */
    private String settlementType;

    /** 金额口径（快照） */
    private String currencyType;

    /** 买家用户 ID */
    private Long buyerUserId;

    /** 买家名称（快照） */
    private String buyerName;

    /** 状态 */
    private String status;

    /** 订单总额 */
    private BigDecimal totalAmount;

    /** 商品总件数 */
    private Integer totalQuantity;

    /** 明细行数 */
    private Integer itemCount;

    /** 幂等键 */
    private String idempotencyKey;

    /** 支付单号 */
    private String payNo;

    /** 支付时间 */
    private LocalDateTime payTime;

    /** 累计退款金额 */
    private BigDecimal refundAmount;

    /** 退款完成时间 */
    private LocalDateTime refundTime;

    /** 发货时间 */
    private LocalDateTime shipTime;

    /** 完成时间 */
    private LocalDateTime completeTime;

    /** 取消时间 */
    private LocalDateTime cancelTime;

    /** 取消原因 */
    private String cancelReason;

    /** 待支付超时时间 */
    private LocalDateTime expireTime;

    /** 买家备注 */
    private String remark;
}
