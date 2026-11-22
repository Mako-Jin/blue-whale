package com.bluewhale.oa.hr.application.command;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 员工扩展档案保存命令（按租户 + 用户ID 幂等 upsert）。
 *
 * @author BlueWhale
 */
@Data
public class EmployeeProfileSaveCommand implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户ID */
    @NotNull(message = "{validation.oa.employee-profile.user-id-required}")
    private Long userId;

    /** 工号 */
    @Size(max = 64, message = "{validation.oa.employee-profile.employee-no-size}")
    private String employeeNo;

    /** 部门ID */
    private Long deptId;

    /** 入职日期 */
    private LocalDate hireDate;

    /** 转正日期 */
    private LocalDate regularDate;

    /** 生日 */
    private LocalDate birthday;

    /** 在职状态 */
    @Pattern(regexp = "ACTIVE|PROBATION|REGULAR|LEFT", message = "{validation.oa.employee-profile.employment-status-pattern}")
    private String employmentStatus;

    /** 离职日期 */
    private LocalDate leaveDate;

    /** 文化墙是否公开展示 */
    private Boolean cultureVisible;

    /** 备注 */
    @Size(max = 255, message = "{validation.oa.employee-profile.remark-size}")
    private String remark;
}
