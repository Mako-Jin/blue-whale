package com.bluewhale.erp.hr.application.view;

import com.bluewhale.erp.hr.domain.model.Payslip;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 工资条视图。
 *
 * @author BlueWhale
 */
@Data
public class PayslipView {

    /** 主键 */
    private Long id;

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

    /**
     * 由领域对象构建视图。
     *
     * @param payslip 工资条
     * @return 视图
     */
    public static PayslipView of(Payslip payslip) {
        PayslipView view = new PayslipView();
        view.setId(payslip.getId());
        view.setPayrollId(payslip.getPayrollId());
        view.setEmployeeNo(payslip.getEmployeeNo());
        view.setEmployeeName(payslip.getEmployeeName());
        view.setOrgCode(payslip.getOrgCode());
        view.setBaseSalary(payslip.getBaseSalary());
        view.setAllowance(payslip.getAllowance());
        view.setOvertimePay(payslip.getOvertimePay());
        view.setLeaveDeduction(payslip.getLeaveDeduction());
        view.setAbsenceDeduction(payslip.getAbsenceDeduction());
        view.setGrossPay(payslip.getGrossPay());
        view.setSocialInsurance(payslip.getSocialInsurance());
        view.setHousingFund(payslip.getHousingFund());
        view.setTax(payslip.getTax());
        view.setNetPay(payslip.getNetPay());
        return view;
    }
}
