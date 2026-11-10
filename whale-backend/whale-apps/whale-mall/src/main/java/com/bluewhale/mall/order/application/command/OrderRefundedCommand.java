package com.bluewhale.mall.order.application.command;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 退款回调命令（whale-pay 内网调用）。
 *
 * <p>{@code refundNo} 是发起退款时传给支付侧的业务退款请求号，支付侧原样回传，
 * 对商城而言它就是售后单号——回调靠它定位要结哪张售后单。</p>
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

    /** 业务退款请求号（即售后单号，支付侧原样回传） */
    private String refundNo;

    /** 累计退款金额 */
    private BigDecimal refundAmount;
}
