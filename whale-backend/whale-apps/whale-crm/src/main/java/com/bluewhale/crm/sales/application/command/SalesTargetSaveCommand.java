package com.bluewhale.crm.sales.application.command;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 新建销售目标命令。
 *
 * @author BlueWhale
 */
@Data
public class SalesTargetSaveCommand {

    /** 周期：yyyy-MM 或 yyyy-Qn */
    @NotBlank(message = "{validation.crm.sales-target.period-required}")
    @Size(max = 10, message = "{validation.crm.sales-target.period-size}")
    private String period;

    /** 周期类型：MONTH / QUARTER，默认 MONTH */
    private String periodType;

    /** 范围类型：USER / DEPT，默认 USER */
    private String scopeType;

    /** 负责人（USER 范围使用） */
    private Long ownerId;

    /** 部门（DEPT 范围必填） */
    private Long deptId;

    /** 目标金额 */
    @NotNull(message = "{validation.crm.sales-target.target-amount-required}")
    @DecimalMin(value = "0", message = "{validation.crm.sales-target.target-amount-min}")
    private BigDecimal targetAmount;

    /** 备注 */
    @Size(max = 500, message = "{validation.crm.sales-target.remark-size}")
    private String remark;
}
