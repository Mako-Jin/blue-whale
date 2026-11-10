package com.bluewhale.erp.hr.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link Payslip} 计薪规则单元测试。
 *
 * @author BlueWhale
 */
class PayslipTest {

    @Test
    @DisplayName("无考勤时按基本工资与津贴计薪并代扣社保公积金个税")
    void shouldBuildWithoutAttendance() {
        Payslip payslip = Payslip.build(employee("10000", "1000", "10000"), null, new HrPayrollRates());

        assertEquals(0, payslip.getOvertimePay().compareTo(BigDecimal.ZERO));
        assertEquals(0, payslip.getLeaveDeduction().compareTo(BigDecimal.ZERO));
        assertEquals(0, payslip.getGrossPay().compareTo(new BigDecimal("11000.00")));
        assertEquals(0, payslip.getSocialInsurance().compareTo(new BigDecimal("1050.00")));
        assertEquals(0, payslip.getHousingFund().compareTo(new BigDecimal("700.00")));
        assertEquals(0, payslip.getTax().compareTo(new BigDecimal("215.00")));
        assertEquals(0, payslip.getNetPay().compareTo(new BigDecimal("9035.00")));
    }

    @Test
    @DisplayName("有考勤时计算加班费、请假与缺勤扣款")
    void shouldBuildWithAttendance() {
        Payslip payslip = Payslip.build(employee("10000", "1000", "10000"), attendance(), new HrPayrollRates());

        assertEquals(0, payslip.getOvertimePay().compareTo(new BigDecimal("689.66")));
        assertEquals(0, payslip.getLeaveDeduction().compareTo(new BigDecimal("459.77")));
        assertEquals(0, payslip.getAbsenceDeduction().compareTo(new BigDecimal("459.77")));
        assertEquals(0, payslip.getGrossPay().compareTo(new BigDecimal("10770.12")));
        assertEquals(0, payslip.getTax().compareTo(new BigDecimal("192.01")));
        assertEquals(0, payslip.getNetPay().compareTo(new BigDecimal("8828.11")));
    }

    @Test
    @DisplayName("社保基数为零时按基本工资计提")
    void shouldFallbackToBaseSalary() {
        Payslip payslip = Payslip.build(employee("10000", "0", "0"), null, new HrPayrollRates());

        assertEquals(0, payslip.getSocialInsurance().compareTo(new BigDecimal("1050.00")));
    }

    @Test
    @DisplayName("月度个税按七级超额累进计算")
    void shouldCalculateMonthlyTax() {
        assertEquals(0, Payslip.monthlyTax(new BigDecimal("3000")).compareTo(new BigDecimal("90.00")));
        assertEquals(0, Payslip.monthlyTax(new BigDecimal("30000")).compareTo(new BigDecimal("4840.00")));
        assertEquals(0, Payslip.monthlyTax(new BigDecimal("100000")).compareTo(new BigDecimal("29840.00")));
    }

    /**
     * 构造员工。
     *
     * @param base 基本工资
     * @param allowance 津贴
     * @param socialBase 社保基数
     * @return 员工
     */
    private Employee employee(String base, String allowance, String socialBase) {
        Employee employee = new Employee();
        employee.setEmployeeNo("E001");
        employee.setName("张三");
        employee.setOrgCode("ORG-01");
        employee.setHireDate(LocalDate.of(2026, 1, 1));
        employee.setStatus(HrStatus.EMPLOYEE_ACTIVE);
        employee.setBaseSalary(new BigDecimal(base));
        employee.setAllowance(new BigDecimal(allowance));
        employee.setSocialBase(new BigDecimal(socialBase));
        return employee;
    }

    /**
     * 构造考勤汇总。
     *
     * @return 考勤
     */
    private AttendanceSummary attendance() {
        AttendanceSummary attendance = new AttendanceSummary();
        attendance.setEmployeeNo("E001");
        attendance.setPeriod("2026-11");
        attendance.setWorkDays(new BigDecimal("21.75"));
        attendance.setActualDays(new BigDecimal("20.75"));
        attendance.setLeaveHours(new BigDecimal("8"));
        attendance.setOvertimeHours(new BigDecimal("8"));
        attendance.setAbsentDays(BigDecimal.ONE);
        return attendance;
    }
}
