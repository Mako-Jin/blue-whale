package com.bluewhale.mall.order.application.command;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 退款回调命令（whale-pay 内网调用）。
 *
 * @author BlueWhale
 */
@Data
public class OrderRefundedCommand implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 商城订单号 */
    private String orderNo;

    /** 支付单号 */
    private String payNo;

    /** 退款单号（whale-pay 侧） */
    private String refundNo;

    /** 累计退款金额 */
    private BigDecimal refundAmount;
}
