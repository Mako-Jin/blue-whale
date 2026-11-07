package com.bluewhale.crm.commerce.application.command;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 回款分期命令。
 *
 * @author BlueWhale
 */
@Data
public class CollectionInstallmentCommand {

    /** 计划回款金额 */
    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal plannedAmount;

    /** 计划回款日期 */
    @NotNull
    private LocalDate plannedDate;

    /** 催收优先级覆盖：HIGH / MEDIUM / LOW */
    private String priorityOverride;

    /** 备注 */
    private String remark;
}
