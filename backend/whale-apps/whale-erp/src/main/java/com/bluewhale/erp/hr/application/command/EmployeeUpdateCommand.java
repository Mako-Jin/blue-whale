package com.bluewhale.erp.hr.application.command;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 编辑员工档案命令（编号不可修改，组织变动请走异动单）。
 *
 * @author BlueWhale
 */
@Data
public class EmployeeUpdateCommand {

    /** 姓名 */
    @NotBlank
    private String name;

    /** 性别 */
    private String gender;

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 岗位 */
    private String position;

    /** 月基本工资 */
    private BigDecimal baseSalary;

    /** 固定津贴 */
    private BigDecimal allowance;

    /** 社保/公积金基数 */
    private BigDecimal socialBase;

    /** 平台账号 ID */
    private Long userId;

    /** 备注 */
    private String remark;
}
