package com.bluewhale.erp.hr.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 工资条。
 *
 * <p>计薪规则：应发 = 基本工资 + 津贴 + 加班费 − 请假扣款 − 缺勤扣款；
 * 社保/公积金按计薪基数比例扣减；个税按 7 级超额累进（月度）；实发 = 应发 − 社保 − 公积金 − 个税。</p>
 *
 * @author BlueWhale
 */
@Data
public class Payslip {

    /** 金额精度 */
    private static final int MONEY_SCALE = 2;

    /** 比例精度 */
    private static final int RATE_SCALE = 4;

    /** 主键 */
    private Long id;

    /** 薪资批次主键 */
    private Long payrollId;

    /** 员工编号 */
    private String employeeNo;

    /** 员工姓名快照 */
    private String employeeName;

    /** 组织编码快照 */
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
     * 按员工档案、考勤与参数计算工资条。
     *
     * @param employee 员工
     * @param attendance 考勤（可为空，按无请假/加班/缺勤处理）
     * @param rates 薪资参数
     * @return 工资条
     */
    public static Payslip build(Employee employee, AttendanceSummary attendance, HrPayrollRates rates) {
        Payslip payslip = new Payslip();
        payslip.employeeNo = employee.getEmployeeNo();
        payslip.employeeName = employee.getName();
        payslip.orgCode = employee.getOrgCode();
        BigDecimal base = money(employee.getBaseSalary());
        BigDecimal allowance = money(employee.getAllowance());
        payslip.baseSalary = base;
        payslip.allowance = allowance;

        BigDecimal monthlyDays = rates.getMonthlyWorkDays();
        BigDecimal hourlyRate = base.divide(monthlyDays.multiply(rates.getDailyHours()), RATE_SCALE, RoundingMode.HALF_UP);
        BigDecimal leaveHours = decimal(attendance == null ? null : attendance.getLeaveHours());
        BigDecimal overtimeHours = decimal(attendance == null ? null : attendance.getOvertimeHours());
        BigDecimal absentDays = decimal(attendance == null ? null : attendance.getAbsentDays());

        payslip.overtimePay = money(hourlyRate.multiply(overtimeHours).multiply(rates.getOvertimeMultiplier()));
        payslip.leaveDeduction = money(hourlyRate.multiply(leaveHours));
        payslip.absenceDeduction = money(base.divide(monthlyDays, RATE_SCALE, RoundingMode.HALF_UP).multiply(absentDays));

        BigDecimal gross = base.add(allowance).add(payslip.overtimePay)
                .subtract(payslip.leaveDeduction).subtract(payslip.absenceDeduction);
        if (gross.signum() < 0) {
            gross = BigDecimal.ZERO;
        }
        payslip.grossPay = money(gross);

        BigDecimal contributionBase = money(employee.contributionBase());
        payslip.socialInsurance = money(contributionBase.multiply(rates.getSocialInsuranceRate()));
        payslip.housingFund = money(contributionBase.multiply(rates.getHousingFundRate()));

        BigDecimal taxable = payslip.grossPay.subtract(payslip.socialInsurance)
                .subtract(payslip.housingFund).subtract(rates.getTaxThreshold());
        if (taxable.signum() < 0) {
            taxable = BigDecimal.ZERO;
        }
        payslip.tax = money(monthlyTax(taxable));

        payslip.netPay = money(payslip.grossPay.subtract(payslip.socialInsurance)
                .subtract(payslip.housingFund).subtract(payslip.tax));
        return payslip;
    }

    /**
     * 月度个税（7 级超额累进，速算扣除数）。
     *
     * @param taxable 应纳税所得额
     * @return 税额
     */
    static BigDecimal monthlyTax(BigDecimal taxable) {
        if (taxable.compareTo(new BigDecimal("3000")) <= 0) {
            return taxable.multiply(new BigDecimal("0.03"));
        }
        if (taxable.compareTo(new BigDecimal("12000")) <= 0) {
            return taxable.multiply(new BigDecimal("0.10")).subtract(new BigDecimal("210"));
        }
        if (taxable.compareTo(new BigDecimal("25000")) <= 0) {
            return taxable.multiply(new BigDecimal("0.20")).subtract(new BigDecimal("1410"));
        }
        if (taxable.compareTo(new BigDecimal("35000")) <= 0) {
            return taxable.multiply(new BigDecimal("0.25")).subtract(new BigDecimal("2660"));
        }
        if (taxable.compareTo(new BigDecimal("55000")) <= 0) {
            return taxable.multiply(new BigDecimal("0.30")).subtract(new BigDecimal("4410"));
        }
        if (taxable.compareTo(new BigDecimal("80000")) <= 0) {
            return taxable.multiply(new BigDecimal("0.35")).subtract(new BigDecimal("7160"));
        }
        return taxable.multiply(new BigDecimal("0.45")).subtract(new BigDecimal("15160"));
    }

    /**
     * 金额保留 2 位小数。
     *
     * @param value 金额
     * @return 规范化金额
     */
    private static BigDecimal money(BigDecimal value) {
        return decimal(value).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 空值转零。
     *
     * @param value 数值
     * @return 非空数值
     */
    private static BigDecimal decimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
