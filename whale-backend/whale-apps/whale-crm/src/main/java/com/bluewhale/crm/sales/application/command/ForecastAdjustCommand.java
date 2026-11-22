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
    @NotBlank(message = "{validation.crm.forecast-adjust.field-code-required}")
    private String fieldCode;

    /** 调整后金额 */
    @NotNull(message = "{validation.crm.forecast-adjust.amount-required}")
    @DecimalMin(value = "0", message = "{validation.crm.forecast-adjust.amount-min}")
    private BigDecimal amount;

    /** 调整原因 */
    @NotBlank(message = "{validation.crm.forecast-adjust.reason-required}")
    @Size(max = 500, message = "{validation.crm.forecast-adjust.reason-size}")
    private String reason;
}
