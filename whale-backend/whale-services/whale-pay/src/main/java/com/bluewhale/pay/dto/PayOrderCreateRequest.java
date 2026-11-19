package com.bluewhale.pay.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 支付单创建请求
 *
 * @author BlueWhale
 */
@Data
public class PayOrderCreateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 支付单号（业务唯一） */
    @NotBlank(message = "{validation.pay.order.order-no-required}")
    @Size(max = 64, message = "{validation.pay.order.order-no-size}")
    private String orderNo;

    /** 业务标识（外部订单号 / 业务键） */
    @Size(max = 128, message = "{validation.pay.order.business-key-size}")
    private String businessKey;

    /** 支付金额（必须大于 0，最多 2 位小数） */
    @NotNull(message = "{validation.pay.order.amount-required}")
    @DecimalMin(value = "0.01", message = "{validation.pay.order.amount-min}")
    @Digits(integer = 16, fraction = 2, message = "{validation.pay.order.amount-digits}")
    private BigDecimal amount;

    /** 币种，默认 CNY */
    @NotBlank(message = "{validation.pay.order.currency-required}")
    @Size(max = 8, message = "{validation.pay.order.currency-size}")
    private String currency;

    /** 支付渠道，如 ALIPAY / WECHAT / BALANCE */
    @Size(max = 32, message = "{validation.pay.order.channel-size}")
    private String channel;

    /** 备注 */
    @Size(max = 255, message = "{validation.pay.order.remark-size}")
    private String remark;
}
