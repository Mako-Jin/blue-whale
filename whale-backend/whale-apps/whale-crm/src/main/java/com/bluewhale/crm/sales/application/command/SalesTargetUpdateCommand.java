package com.bluewhale.crm.sales.application.command;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 调整销售目标命令。周期与范围不可变更。
 *
 * @author BlueWhale
 */
@Data
public class SalesTargetUpdateCommand {

    /** 目标金额 */
    @NotNull(message = "{validation.crm.sales-target.target-amount-required}")
    @DecimalMin(value = "0", message = "{validation.crm.sales-target.target-amount-min}")
    private BigDecimal targetAmount;

    /** 调整原因 */
    @Size(max = 500, message = "{validation.crm.sales-target.reason-size}")
    private String reason;

    /** 备注 */
    @Size(max = 500, message = "{validation.crm.sales-target.remark-size}")
    private String remark;
}
