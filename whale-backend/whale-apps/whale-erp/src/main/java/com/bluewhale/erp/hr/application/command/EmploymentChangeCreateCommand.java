package com.bluewhale.erp.hr.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 新增员工异动命令。
 *
 * @author BlueWhale
 */
@Data
public class EmploymentChangeCreateCommand {

    /** 员工编号 */
    @NotBlank
    private String employeeNo;

    /** 类型：HIRE / TRANSFER / PROMOTION / RESIGN / RETURN */
    @NotBlank
    private String changeType;

    /** 生效日期 */
    @NotNull
    private LocalDate effectiveDate;

    /** 目标组织编码 */
    private String toOrgCode;

    /** 目标岗位 */
    private String toPosition;

    /** 目标月薪 */
    private BigDecimal toSalary;

    /** 备注 */
    private String remark;
}
