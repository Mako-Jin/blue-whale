package com.bluewhale.erp.hr.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 新增员工命令。
 *
 * @author BlueWhale
 */
@Data
public class EmployeeCreateCommand {

    /** 员工编号 */
    @NotBlank
    private String employeeNo;

    /** 姓名 */
    @NotBlank
    private String name;

    /** 性别 */
    private String gender;

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 所属组织编码 */
    @NotBlank
    private String orgCode;

    /** 岗位 */
    private String position;

    /** 入职日期 */
    @NotNull
    private LocalDate hireDate;

    /** 月基本工资 */
    private BigDecimal baseSalary;

    /** 固定津贴 */
    private BigDecimal allowance;

    /** 社保/公积金基数（0 表示取基本工资） */
    private BigDecimal socialBase;

    /** 平台账号 ID */
    private Long userId;

    /** 备注 */
    private String remark;
}
