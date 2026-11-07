package com.bluewhale.mall.order.application.view;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 订单视图（列表与详情共用，列表按需截断明细）。
 *
 * @author BlueWhale
 */
@Data
public class OrderView {

    /** 订单 ID */
    private Long id;

    /** 订单号 */
    private String orderNo;

    /** 渠道 ID */
    private Long channelId;

    /** 渠道编码 */
    private String channelCode;

    /** 商城形态 */
    private String mode;

    /** 结算方式 */
    private String settlementType;

    /** 金额口径 */
    private String currencyType;

    /** 买家用户 ID */
    private Long buyerUserId;

    /** 买家名称 */
    private String buyerName;

    /** 状态 */
    private String status;

    /** 订单总额（应付 = 商品总额 - 优惠 + 运费） */
    private BigDecimal totalAmount;

    /** 商品总额（不含优惠与运费，报表成交额看这个） */
    private BigDecimal goodsAmount;

    /** 优惠金额 */
    private BigDecimal discountAmount;

    /** 运费 */
    private BigDecimal freightAmount;

    /** 使用的券编码（快照） */
    private String couponCode;

    /** 商品总件数 */
    private Integer totalQuantity;

    /** 明细行数 */
    private Integer itemCount;

    /** 支付单号 */
    private String payNo;

    /** 支付时间 */
    private LocalDateTime payTime;

    /** 累计退款金额（部分退款时大于 0 且小于总额） */
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

    /** 买家备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 明细 */
    private List<OrderLineView> items = new ArrayList<>();
}
