package com.bluewhale.erp.hr.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 工资条实体，对应表 {@code erp_hr_payslip}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_hr_payslip")
public class PayslipEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 薪资批次主键 */
    private Long payrollId;

    /** 员工编号 */
    private String employeeNo;

    /** 员工姓名 */
    private String employeeName;

    /** 组织编码 */
    private String orgCode;

    /** 基本工资 */
    private BigDecimal baseSalary;

    /** 固定津贴 */
    private BigDecimal allowance;

    /** 加班费 */
    private BigDecimal overtimePay;

    /** 请假扣款 */
    private BigDecimal leaveDeduction;

    /** 缺勤扣款 */
    private BigDecimal absenceDeduction;

    /** 应发工资 */
    private BigDecimal grossPay;

    /** 社保个人部分 */
    private BigDecimal socialInsurance;

    /** 公积金个人部分 */
    private BigDecimal housingFund;

    /** 个人所得税 */
    private BigDecimal tax;

    /** 实发工资 */
    private BigDecimal netPay;
}
