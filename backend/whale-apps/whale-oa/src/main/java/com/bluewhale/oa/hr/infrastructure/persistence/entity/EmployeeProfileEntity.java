package com.bluewhale.oa.hr.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 员工扩展档案实体，对应表 {@code oa_employee_profile}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_employee_profile")
public class EmployeeProfileEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

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
}
