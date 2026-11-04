package com.bluewhale.erp.hr.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.hr.application.command.AttendanceSaveCommand;
import com.bluewhale.erp.hr.application.view.AttendanceView;
import com.bluewhale.erp.hr.domain.model.AttendanceSummary;
import com.bluewhale.erp.hr.domain.model.Employee;
import com.bluewhale.erp.hr.domain.model.HrStatus;
import com.bluewhale.erp.hr.domain.repository.AttendanceRepository;
import com.bluewhale.erp.hr.domain.repository.EmployeeRepository;
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
 * {@link AttendanceAppServiceImpl} 单元测试。
 *
 * @author BlueWhale
 */
class AttendanceAppServiceImplTest {

    private final AttendanceRepository attendanceRepository = mock(AttendanceRepository.class);

    private final EmployeeRepository employeeRepository = mock(EmployeeRepository.class);

    private final AttendanceAppServiceImpl service = new AttendanceAppServiceImpl(attendanceRepository,
            employeeRepository);

    @Test
    @DisplayName("首次登记考勤写入")
    void shouldInsertAttendance() {
        when(employeeRepository.findByEmployeeNo("E001")).thenReturn(Optional.of(employee()));
        when(attendanceRepository.find("E001", "2026-11")).thenReturn(Optional.empty());

        AttendanceView view = service.save(command("2026-11"));

        assertEquals("E001", view.getEmployeeNo());
        assertEquals(0, view.getOvertimeHours().compareTo(new BigDecimal("8")));
        verify(attendanceRepository).insert(any(AttendanceSummary.class));
    }

    @Test
    @DisplayName("已有考勤按员工与期间更新")
    void shouldUpdateAttendance() {
        when(employeeRepository.findByEmployeeNo("E001")).thenReturn(Optional.of(employee()));
        AttendanceSummary existing = new AttendanceSummary();
        existing.setId(3L);
        existing.setEmployeeNo("E001");
        existing.setPeriod("2026-11");
        when(attendanceRepository.find("E001", "2026-11")).thenReturn(Optional.of(existing));

        AttendanceView view = service.save(command("2026-11"));

        assertEquals(3L, view.getId());
        assertEquals(0, view.getLeaveHours().compareTo(new BigDecimal("8")));
        verify(attendanceRepository).update(any(AttendanceSummary.class));
        verify(attendanceRepository, never()).insert(any(AttendanceSummary.class));
    }

    @Test
    @DisplayName("期间格式非法拒绝")
    void shouldRejectInvalidPeriod() {
        BizException exception = assertThrows(BizException.class, () -> service.save(command("202611")));

        assertEquals(ErpErrorCode.HR_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("员工不存在拒绝登记")
    void shouldRejectMissingEmployee() {
        when(employeeRepository.findByEmployeeNo("E001")).thenReturn(Optional.empty());

        BizException exception = assertThrows(BizException.class, () -> service.save(command("2026-11")));

        assertEquals(ErpErrorCode.EMPLOYEE_NOT_FOUND, exception.getCode());
    }

    /**
     * 构造考勤命令。
     *
     * @param period 期间
     * @return 命令
     */
    private AttendanceSaveCommand command(String period) {
        AttendanceSaveCommand command = new AttendanceSaveCommand();
        command.setEmployeeNo("E001");
        command.setPeriod(period);
        command.setWorkDays(new BigDecimal("21.75"));
        command.setActualDays(new BigDecimal("21.75"));
        command.setLeaveHours(new BigDecimal("8"));
        command.setOvertimeHours(new BigDecimal("8"));
        command.setAbsentDays(BigDecimal.ZERO);
        command.setRemark("月末汇总");
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
        employee.setHireDate(LocalDate.of(2026, 1, 1));
        employee.setStatus(HrStatus.EMPLOYEE_ACTIVE);
        return employee;
    }
}
