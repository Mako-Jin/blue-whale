package com.bluewhale.pay.dto;

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
    @NotBlank(message = "支付单号不能为空")
    @Size(max = 64, message = "支付单号长度不能超过 64")
    private String orderNo;

    /** 业务标识（外部订单号 / 业务键） */
    @Size(max = 128, message = "业务标识长度不能超过 128")
    private String businessKey;

    /** 支付金额 */
    @NotNull(message = "支付金额不能为空")
    private BigDecimal amount;

    /** 币种，默认 CNY */
    @NotBlank(message = "币种不能为空")
    @Size(max = 8, message = "币种长度不能超过 8")
    private String currency;

    /** 支付渠道，如 ALIPAY / WECHAT / BALANCE */
    @Size(max = 32, message = "支付渠道长度不能超过 32")
    private String channel;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
