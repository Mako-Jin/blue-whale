package com.bluewhale.erp.hr.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 员工档案实体，对应表 {@code erp_hr_employee}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_hr_employee")
public class EmployeeEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 员工编号 */
    private String employeeNo;

    /** 姓名 */
    private String name;

    /** 性别 */
    private String gender;

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 组织编码 */
    private String orgCode;

    /** 岗位 */
    private String position;

    /** 入职日期 */
    private LocalDate hireDate;

    /** 离职日期 */
    private LocalDate leaveDate;

    /** 状态 */
    private String status;

    /** 月基本工资 */
    private BigDecimal baseSalary;

    /** 固定津贴 */
    private BigDecimal allowance;

    /** 社保基数 */
    private BigDecimal socialBase;

    /** 平台账号 ID */
    private Long userId;

    /** 备注 */
    private String remark;
}
