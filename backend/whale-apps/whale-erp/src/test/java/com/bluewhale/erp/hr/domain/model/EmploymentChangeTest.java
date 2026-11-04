package com.bluewhale.erp.hr.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.erp.common.ErpErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link EmploymentChange} 异动类型与回写单元测试。
 *
 * @author BlueWhale
 */
class EmploymentChangeTest {

    @Test
    @DisplayName("非法异动类型拒绝")
    void shouldRejectUnknownType() {
        EmploymentChange change = transferChange();
        change.setChangeType("UNKNOWN");

        BizException exception = assertThrows(BizException.class, change::validate);

        assertEquals(ErpErrorCode.HR_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("调动未填目标组织拒绝")
    void shouldRejectTransferWithoutTargetOrg() {
        EmploymentChange change = transferChange();
        change.setToOrgCode(null);

        BizException exception = assertThrows(BizException.class, change::validate);

        assertEquals(ErpErrorCode.HR_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("调动生效回写员工组织、岗位与月薪")
    void shouldApplyTransfer() {
        EmploymentChange change = transferChange();
        Employee employee = employee();

        change.effective();
        change.applyTo(employee);

        assertEquals(HrStatus.CHANGE_EFFECTIVE, change.getStatus());
        assertEquals("ORG-02", employee.getOrgCode());
        assertEquals("主管", employee.getPosition());
        assertEquals(0, employee.getBaseSalary().compareTo(new BigDecimal("12000")));
    }

    @Test
    @DisplayName("离职生效置员工为 LEFT")
    void shouldApplyResign() {
        EmploymentChange change = transferChange();
        change.setChangeType(HrStatus.CHANGE_RESIGN);
        change.setToOrgCode(null);
        Employee employee = employee();

        change.effective();
        change.applyTo(employee);

        assertEquals(HrStatus.EMPLOYEE_LEFT, employee.getStatus());
        assertEquals(LocalDate.of(2026, 11, 30), employee.getLeaveDate());
    }

    @Test
    @DisplayName("复职生效恢复在职并按目标组织调动")
    void shouldApplyReturn() {
        EmploymentChange change = transferChange();
        change.setChangeType(HrStatus.CHANGE_RETURN);
        Employee employee = employee();
        employee.setStatus(HrStatus.EMPLOYEE_LEFT);

        change.effective();
        change.applyTo(employee);

        assertEquals(HrStatus.EMPLOYEE_ACTIVE, employee.getStatus());
        assertEquals("ORG-02", employee.getOrgCode());
    }

    @Test
    @DisplayName("非草稿异动单不能重复生效或取消")
    void shouldRejectSecondEffective() {
        EmploymentChange change = transferChange();
        change.effective();

        BizException exception = assertThrows(BizException.class, change::cancel);

        assertEquals(ErpErrorCode.HR_STATE_CONFLICT, exception.getCode());
    }

    /**
     * 构造调动异动单。
     *
     * @return 异动单
     */
    private EmploymentChange transferChange() {
        EmploymentChange change = new EmploymentChange();
        change.setChangeNo("HC-001");
        change.setEmployeeNo("E001");
        change.setChangeType(HrStatus.CHANGE_TRANSFER);
        change.setEffectiveDate(LocalDate.of(2026, 11, 30));
        change.setFromOrgCode("ORG-01");
        change.setToOrgCode("ORG-02");
        change.setFromPosition("专员");
        change.setToPosition("主管");
        change.setFromSalary(new BigDecimal("10000"));
        change.setToSalary(new BigDecimal("12000"));
        change.setStatus(HrStatus.CHANGE_DRAFT);
        return change;
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
        employee.setSocialBase(BigDecimal.ZERO);
        return employee;
    }
}
