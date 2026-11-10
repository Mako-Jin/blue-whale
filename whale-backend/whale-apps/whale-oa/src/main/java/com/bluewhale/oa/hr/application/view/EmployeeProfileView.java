package com.bluewhale.oa.hr.application.view;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 员工扩展档案视图。
 *
 * @author BlueWhale
 */
@Data
public class EmployeeProfileView implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 工号 */
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
    private String employmentStatus;

    /** 离职日期 */
    private LocalDate leaveDate;

    /** 文化墙是否公开展示 */
    private Boolean cultureVisible;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
