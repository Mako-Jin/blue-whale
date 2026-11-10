package com.bluewhale.erp.hr.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 薪资批次实体，对应表 {@code erp_hr_payroll_run}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_hr_payroll_run")
public class PayrollRunEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 批次号 */
    private String runNo;

    /** 薪资期间 */
    private String period;

    /** 状态 */
    private String status;

    /** 员工人数 */
    private Integer employeeCount;

    /** 应发合计 */
    private BigDecimal totalGross;

    /** 社保合计 */
    private BigDecimal totalSocialInsurance;

    /** 公积金合计 */
    private BigDecimal totalHousingFund;

    /** 个税合计 */
    private BigDecimal totalTax;

    /** 实发合计 */
    private BigDecimal totalNet;

    /** 备注 */
    private String remark;
}
