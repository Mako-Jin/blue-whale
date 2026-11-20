package com.bluewhale.pay.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 内网创建支付单请求。
 *
 * <p>由业务服务（如 whale-mall）经 {@code /pay/internal/order} 调用；
 * 金额以业务方订单应付总额为准，按 {@code orderNo} 幂等。</p>
 *
 * @author BlueWhale
 */
@Data
public class PayInternalCreateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 支付单号（传业务单号，如商城订单号，租户内唯一） */
    @NotBlank(message = "{validation.pay.internal-create.order-no-required}")
    private String orderNo;

    /** 业务方类型（MALL_ORDER 等） */
    @NotBlank(message = "{validation.pay.internal-create.biz-type-required}")
    private String bizType;

    /** 支付金额 */
    @NotNull(message = "{validation.pay.internal-create.amount-required}")
    private BigDecimal amount;

    /** 币种（默认 CNY） */
    private String currency;

    /** 渠道（默认 MOCK） */
    private String channel;

    /** 业务回调地址（格式：&lt;服务名&gt;/&lt;路径&gt;） */
    @NotBlank(message = "{validation.pay.internal-create.notify-url-required}")
    private String notifyUrl;

    /** 备注 */
    private String remark;
}
