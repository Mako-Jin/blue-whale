package com.bluewhale.erp.hr.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 员工异动单实体，对应表 {@code erp_hr_employment_change}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_hr_employment_change")
public class EmploymentChangeEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 异动单号 */
    private String changeNo;

    /** 员工编号 */
    private String employeeNo;

    /** 异动类型 */
    private String changeType;

    /** 生效日期 */
    private LocalDate effectiveDate;

    /** 原组织编码 */
    private String fromOrgCode;

    /** 新组织编码 */
    private String toOrgCode;

    /** 原岗位 */
    private String fromPosition;

    /** 新岗位 */
    private String toPosition;

    /** 原月薪 */
    private BigDecimal fromSalary;

    /** 新月薪 */
    private BigDecimal toSalary;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;
}
