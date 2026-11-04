package com.bluewhale.erp.hr.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.erp.common.ErpErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link Employee} 档案不变量单元测试。
 *
 * @author BlueWhale
 */
class EmployeeTest {

    @Test
    @DisplayName("建档必填项缺失时拒绝")
    void shouldRejectMissingFields() {
        Employee employee = new Employee();
        employee.setEmployeeNo("E001");

        BizException exception = assertThrows(BizException.class, employee::validateForCreate);

        assertEquals(ErpErrorCode.HR_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("薪资为负数时拒绝")
    void shouldRejectNegativeSalary() {
        Employee employee = employee();
        employee.setBaseSalary(new BigDecimal("-1"));

        BizException exception = assertThrows(BizException.class, employee::validateForCreate);

        assertEquals(ErpErrorCode.HR_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("调动更新组织、岗位与月薪")
    void shouldTransfer() {
        Employee employee = employee();

        employee.transfer("ORG-02", "主管", new BigDecimal("12000"));

        assertEquals("ORG-02", employee.getOrgCode());
        assertEquals("主管", employee.getPosition());
        assertEquals(0, employee.getBaseSalary().compareTo(new BigDecimal("12000")));
    }

    @Test
    @DisplayName("离职早于入职日期时拒绝")
    void shouldRejectResignBeforeHire() {
        Employee employee = employee();

        BizException exception = assertThrows(BizException.class, () -> employee.resign(LocalDate.of(2025, 12, 1)));

        assertEquals(ErpErrorCode.HR_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("离职后置为 LEFT 并登记离职日期，复职清空")
    void shouldResignAndActivate() {
        Employee employee = employee();

        employee.resign(LocalDate.of(2026, 11, 30));
        assertEquals(HrStatus.EMPLOYEE_LEFT, employee.getStatus());
        assertEquals(LocalDate.of(2026, 11, 30), employee.getLeaveDate());

        employee.activate();
        assertEquals(HrStatus.EMPLOYEE_ACTIVE, employee.getStatus());
        assertNull(employee.getLeaveDate());
    }

    @Test
    @DisplayName("离职员工不能调动")
    void shouldRejectTransferForLeftEmployee() {
        Employee employee = employee();
        employee.setStatus(HrStatus.EMPLOYEE_LEFT);

        BizException exception = assertThrows(BizException.class,
                () -> employee.transfer("ORG-02", "主管", BigDecimal.ONE));

        assertEquals(ErpErrorCode.HR_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("计薪基数优先取社保基数")
    void shouldUseSocialBaseWhenPositive() {
        Employee employee = employee();
        assertEquals(0, employee.contributionBase().compareTo(new BigDecimal("8000")));

        employee.setSocialBase(BigDecimal.ZERO);
        assertEquals(0, employee.contributionBase().compareTo(new BigDecimal("10000")));
    }

    /**
     * 构造在职员工。
     *
     * @return 员工
     */
    private Employee employee() {
        Employee employee = new Employee();
        employee.setEmployeeNo("E001");
        employee.setName("张三");
        employee.setOrgCode("ORG-01");
        employee.setPosition("专员");
        employee.setHireDate(LocalDate.of(2026, 1, 1));
        employee.setStatus(HrStatus.EMPLOYEE_ACTIVE);
        employee.setBaseSalary(new BigDecimal("10000"));
        employee.setAllowance(BigDecimal.ZERO);
        employee.setSocialBase(new BigDecimal("8000"));
        return employee;
    }
}
