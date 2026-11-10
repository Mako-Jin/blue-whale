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
    @NotNull(message = "目标金额不能为空")
    @DecimalMin(value = "0", message = "目标金额不得为负数")
    private BigDecimal targetAmount;

    /** 调整原因 */
    @Size(max = 500, message = "调整原因长度不能超过 500")
    private String reason;

    /** 备注 */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;
}
