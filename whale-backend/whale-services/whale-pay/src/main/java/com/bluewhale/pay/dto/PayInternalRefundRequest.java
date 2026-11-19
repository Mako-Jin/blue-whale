package com.bluewhale.pay.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 内网退款请求。
 *
 * <p>由业务服务经 {@code /pay/internal/refund} 调用；按支付单号定位，
 * 校验累计退款额不超过支付金额。</p>
 *
 * <p>{@code refundNo} 是业务方生成的退款请求号（如商城售后单号）。
 * 调用方网络超时重试时带同一个号，支付侧按 {@code uk_tenant_refund_no} 判重直接返回原单，
 * 不会重复出账。不传则由支付侧自动生成——此时调用必须保证不重试，否则可能多退。</p>
 *
 * @author BlueWhale
 */
@Data
public class PayInternalRefundRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 支付单号 */
    @NotBlank(message = "{validation.pay.internal-refund.pay-order-no-required}")
    private String payOrderNo;

    /** 业务退款请求号（幂等键，可空） */
    private String refundNo;

    /** 本次退款金额 */
    @NotNull(message = "{validation.pay.internal-refund.amount-required}")
    private BigDecimal amount;

    /** 退款原因 */
    private String reason;
}
