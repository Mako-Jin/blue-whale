package com.bluewhale.crm.sales.application.command;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 销售预测手工调整命令。
 *
 * @author BlueWhale
 */
@Data
public class ForecastAdjustCommand {

    /** 调整字段：PIPELINE / BEST / COMMIT */
    @NotBlank(message = "调整字段不能为空")
    private String fieldCode;

    /** 调整后金额 */
    @NotNull(message = "调整后金额不能为空")
    @DecimalMin(value = "0", message = "调整后金额不得为负数")
    private BigDecimal amount;

    /** 调整原因 */
    @NotBlank(message = "调整原因不能为空")
    @Size(max = 500, message = "调整原因长度不能超过 500")
    private String reason;
}
