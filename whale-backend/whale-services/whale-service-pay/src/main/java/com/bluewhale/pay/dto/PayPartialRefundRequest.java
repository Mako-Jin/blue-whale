package com.bluewhale.pay.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 管理端部分退款请求。
 *
 * <p>{@code clientRequestId} 作为退款单号（幂等键）透传：同号重试返回原退款单，
 * 不重复出账；为空则由支付侧生成，适用于一次性操作。</p>
 *
 * @author BlueWhale
 */
@Data
public class PayPartialRefundRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 本次退款金额 */
    @NotNull(message = "{validation.pay.partial-refund.amount-required}")
    @DecimalMin(value = "0.01", message = "{validation.pay.partial-refund.amount-min}")
    @Digits(integer = 16, fraction = 2, message = "{validation.pay.partial-refund.amount-digits}")
    private BigDecimal amount;

    /** 退款原因 */
    @Size(max = 255, message = "{validation.pay.partial-refund.reason-size}")
    private String reason;

    /** 业务退款请求号（幂等键，可空） */
    @Size(max = 64, message = "{validation.pay.partial-refund.client-request-id-size}")
    private String clientRequestId;
}
