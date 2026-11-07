package com.bluewhale.mall.order.application.command;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 支付结果回调命令（内网，供 {@code whale-pay} 调用）。
 *
 * @author BlueWhale
 */
@Data
public class OrderPaidCommand {

    /** 订单号 */
    @NotBlank(message = "订单号不能为空")
    private String orderNo;

    /** 支付单号 */
    @NotBlank(message = "支付单号不能为空")
    private String payNo;

    /** 实付金额（用于对账校验，可为空表示不校验） */
    private BigDecimal paidAmount;
}
