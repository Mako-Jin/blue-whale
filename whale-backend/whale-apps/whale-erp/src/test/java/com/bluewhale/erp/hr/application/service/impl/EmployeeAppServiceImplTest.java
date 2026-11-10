package com.bluewhale.erp.hr.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.hr.application.command.EmployeeCreateCommand;
import com.bluewhale.erp.hr.application.command.EmployeeUpdateCommand;
import com.bluewhale.erp.hr.application.command.EmploymentChangeCreateCommand;
import com.bluewhale.erp.hr.application.view.EmployeeView;
import com.bluewhale.erp.hr.application.view.EmploymentChangeView;
import com.bluewhale.erp.hr.domain.model.Employee;
import com.bluewhale.erp.hr.domain.model.EmploymentChange;
import com.bluewhale.erp.hr.domain.model.HrStatus;
import com.bluewhale.erp.hr.domain.model.OrgUnit;
import com.bluewhale.erp.hr.domain.repository.EmployeeRepository;
import com.bluewhale.erp.hr.domain.repository.EmploymentChangeRepository;
import com.bluewhale.erp.hr.domain.repository.HrOrgRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link EmployeeAppServiceImpl} 单元测试。
 *
 * @author BlueWhale
 */
class EmployeeAppServiceImplTest {

    private final EmployeeRepository employeeRepository = mock(EmployeeRepository.class);

    private final EmploymentChangeRepository changeRepository = mock(EmploymentChangeRepository.class);

    private final HrOrgRepository orgRepository = mock(HrOrgRepository.class);

    private final EmployeeAppServiceImpl service = new EmployeeAppServiceImpl(employeeRepository,
            changeRepository, orgRepository);

    @Test
    @DisplayName("建档成功并置为在职")
    void shouldCreateEmployee() {
        when(employeeRepository.findByEmployeeNo("E001")).thenReturn(Optional.empty());
        when(orgRepository.findByCode("ORG-01")).thenReturn(Optional.of(orgUnit(HrStatus.ORG_ENABLED)));

        EmployeeView view = service.create(createCommand());

        assertEquals(HrStatus.EMPLOYEE_ACTIVE, view.getStatus());
        assertEquals(0, view.getBaseSalary().compareTo(new BigDecimal("10000")));
        assertEquals(0, view.getSocialBase().compareTo(BigDecimal.ZERO));
        verify(employeeRepository).insert(any(Employee.class));
    }

    @Test
    @DisplayName("员工编号重复拒绝建档")
    void shouldRejectDuplicateEmployeeNo() {
        when(employeeRepository.findByEmployeeNo("E001")).thenReturn(Optional.of(employee()));

        BizException exception = assertThrows(BizException.class, () -> service.create(createCommand()));

        assertEquals(ErpErrorCode.EMPLOYEE_CODE_CONFLICT, exception.getCode());
        verify(employeeRepository, never()).insert(any(Employee.class));
    }

    @Test
    @DisplayName("组织停用时拒绝建档")
    void shouldRejectDisabledOrg() {
        when(employeeRepository.findByEmployeeNo("E001")).thenReturn(Optional.empty());
        when(orgRepository.findByCode("ORG-01")).thenReturn(Optional.of(orgUnit(HrStatus.ORG_DISABLED)));

        BizException exception = assertThrows(BizException.class, () -> service.create(createCommand()));

        assertEquals(ErpErrorCode.HR_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("组织不存在时拒绝建档")
    void shouldRejectMissingOrg() {
        when(employeeRepository.findByEmployeeNo("E001")).thenReturn(Optional.empty());
        when(orgRepository.findByCode("ORG-01")).thenReturn(Optional.empty());

        BizException exception = assertThrows(BizException.class, () -> service.create(createCommand()));

        assertEquals(ErpErrorCode.ORG_NOT_FOUND, exception.getCode());
    }

    @Test
    @DisplayName("编辑员工档案")
    void shouldUpdateEmployee() {
        when(employeeRepository.findByEmployeeNo("E001")).thenReturn(Optional.of(employee()));
        EmployeeUpdateCommand command = new EmployeeUpdateCommand();
        command.setName("张三丰");
        command.setBaseSalary(new BigDecimal("12000"));
        command.setAllowance(new BigDecimal("500"));

        EmployeeView view = service.update("E001", command);

        assertEquals("张三丰", view.getName());
        assertEquals(0, view.getBaseSalary().compareTo(new BigDecimal("12000")));
        assertEquals(0, view.getAllowance().compareTo(new BigDecimal("500")));
        verify(employeeRepository).update(any(Employee.class));
    }

    @Test
    @DisplayName("员工不存在按 404 处理")
    void shouldRejectMissingEmployee() {
        when(employeeRepository.findByEmployeeNo("E001")).thenReturn(Optional.empty());

        BizException exception = assertThrows(BizException.class, () -> service.detail("E001"));

        assertEquals(ErpErrorCode.EMPLOYEE_NOT_FOUND, exception.getCode());
    }

    @Test
    @DisplayName("新增异动单记录原值快照并置草稿")
    void shouldCreateChange() {
        when(employeeRepository.findByEmployeeNo("E001")).thenReturn(Optional.of(employee()));
        when(changeRepository.nextChangeNo()).thenReturn("HC-20261104-0001");

        EmploymentChangeCreateCommand command = new EmploymentChangeCreateCommand();
        command.setEmployeeNo("E001");
        command.setChangeType(HrStatus.CHANGE_TRANSFER);
        command.setEffectiveDate(LocalDate.of(2026, 11, 30));
        command.setToOrgCode("ORG-02");
        command.setToPosition("主管");
        command.setToSalary(new BigDecimal("12000"));

        EmploymentChangeView view = service.createChange(command);

        assertEquals("HC-20261104-0001", view.getChangeNo());
        assertEquals(HrStatus.CHANGE_DRAFT, view.getStatus());
        assertEquals("ORG-01", view.getFromOrgCode());
        assertEquals(0, view.getFromSalary().compareTo(new BigDecimal("10000")));
        verify(changeRepository).insert(any(EmploymentChange.class));
    }

    @Test
    @DisplayName("异动生效回写员工档案")
    void shouldEffectiveChange() {
        EmploymentChange change = new EmploymentChange();
        change.setId(5L);
        change.setChangeNo("HC-001");
        change.setEmployeeNo("E001");
        change.setChangeType(HrStatus.CHANGE_TRANSFER);
        change.setEffectiveDate(LocalDate.of(2026, 11, 30));
        change.setFromOrgCode("ORG-01");
        change.setToOrgCode("ORG-02");
        change.setToPosition("主管");
        change.setToSalary(new BigDecimal("12000"));
        change.setStatus(HrStatus.CHANGE_DRAFT);
        when(changeRepository.findById(5L)).thenReturn(Optional.of(change));
        when(employeeRepository.findByEmployeeNo("E001")).thenReturn(Optional.of(employee()));
        when(orgRepository.findByCode("ORG-02")).thenReturn(Optional.of(orgUnit(HrStatus.ORG_ENABLED)));

        EmploymentChangeView view = service.effectiveChange(5L);

        assertEquals(HrStatus.CHANGE_EFFECTIVE, view.getStatus());
        verify(employeeRepository).update(any(Employee.class));
        verify(changeRepository).update(any(EmploymentChange.class));
    }

    @Test
    @DisplayName("异动单不存在按 404 处理")
    void shouldRejectMissingChange() {
        when(changeRepository.findById(9L)).thenReturn(Optional.empty());

        BizException exception = assertThrows(BizException.class, () -> service.effectiveChange(9L));

        assertEquals(ErpErrorCode.EMPLOYMENT_CHANGE_NOT_FOUND, exception.getCode());
    }

    /**
     * 构造建档命令。
     *
     * @return 命令
     */
    private EmployeeCreateCommand createCommand() {
        EmployeeCreateCommand command = new EmployeeCreateCommand();
        command.setEmployeeNo("E001");
        command.setName("张三");
        command.setOrgCode("ORG-01");
        command.setPosition("专员");
        command.setHireDate(LocalDate.of(2026, 1, 1));
        command.setBaseSalary(new BigDecimal("10000"));
        return command;
    }

    /**
     * 构造员工。
     *
     * @return 员工
     */
    private Employee employee() {
        Employee employee = new Employee();
        employee.setId(1L);
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

    /**
     * 构造组织单元。
     *
     * @param status 状态
     * @return 组织
     */
    private OrgUnit orgUnit(String status) {
        OrgUnit org = new OrgUnit();
        org.setId(2L);
        org.setCode("ORG-01");
        org.setName("总部");
        org.setStatus(status);
        return org;
    }
}
