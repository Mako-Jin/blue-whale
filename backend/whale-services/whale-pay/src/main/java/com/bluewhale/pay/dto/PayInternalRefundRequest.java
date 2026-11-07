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
 * @author BlueWhale
 */
@Data
public class PayInternalRefundRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 支付单号 */
    @NotBlank(message = "支付单号不能为空")
    private String payOrderNo;

    /** 本次退款金额 */
    @NotNull(message = "退款金额不能为空")
    private BigDecimal amount;

    /** 退款原因 */
    private String reason;
}
