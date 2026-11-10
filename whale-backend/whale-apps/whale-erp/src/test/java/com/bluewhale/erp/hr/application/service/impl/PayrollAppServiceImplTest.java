package com.bluewhale.erp.hr.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.common.HrPayrollProperties;
import com.bluewhale.erp.finance.application.command.AutoVoucherCommand;
import com.bluewhale.erp.finance.application.service.AutoVoucherAppService;
import com.bluewhale.erp.hr.application.command.PayrollCreateCommand;
import com.bluewhale.erp.hr.application.view.PayrollRunView;
import com.bluewhale.erp.hr.domain.model.AttendanceSummary;
import com.bluewhale.erp.hr.domain.model.Employee;
import com.bluewhale.erp.hr.domain.model.HrQuery;
import com.bluewhale.erp.hr.domain.model.HrStatus;
import com.bluewhale.erp.hr.domain.model.PayrollRun;
import com.bluewhale.erp.hr.domain.repository.AttendanceRepository;
import com.bluewhale.erp.hr.domain.repository.EmployeeRepository;
import com.bluewhale.erp.hr.domain.repository.PayrollRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PayrollAppServiceImpl} 单元测试。
 *
 * @author BlueWhale
 */
class PayrollAppServiceImplTest {

    private final PayrollRepository payrollRepository = mock(PayrollRepository.class);

    private final EmployeeRepository employeeRepository = mock(EmployeeRepository.class);

    private final AttendanceRepository attendanceRepository = mock(AttendanceRepository.class);

    private final AutoVoucherAppService autoVoucherAppService = mock(AutoVoucherAppService.class);

    private final PayrollAppServiceImpl service = new PayrollAppServiceImpl(payrollRepository,
            employeeRepository, attendanceRepository, autoVoucherAppService, payrollProperties());

    @Test
    @DisplayName("同一期间重复建批次拒绝")
    void shouldRejectDuplicatePeriod() {
        PayrollRun existing = run(HrStatus.PAYROLL_DRAFT);
        when(payrollRepository.findByPeriod("2026-11")).thenReturn(Optional.of(existing));

        BizException exception = assertThrows(BizException.class, () -> service.create(command("2026-11")));

        assertEquals(ErpErrorCode.PAYROLL_PERIOD_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("新建批次生成批次号并置草稿")
    void shouldCreateDraftRun() {
        when(payrollRepository.findByPeriod("2026-11")).thenReturn(Optional.empty());
        when(payrollRepository.nextRunNo("2026-11")).thenReturn("PR-202611-0001");

        PayrollRunView view = service.create(command("2026-11"));

        assertEquals("PR-202611-0001", view.getRunNo());
        assertEquals(HrStatus.PAYROLL_DRAFT, view.getStatus());
        assertEquals(0, view.getTotalGross().compareTo(BigDecimal.ZERO));
        verify(payrollRepository).insert(any(PayrollRun.class));
    }

    @Test
    @DisplayName("期间格式非法拒绝")
    void shouldRejectInvalidPeriod() {
        BizException exception = assertThrows(BizException.class, () -> service.create(command("202611")));

        assertEquals(ErpErrorCode.HR_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("计算薪资：在职员工结合考勤生成工资条并汇总")
    void shouldCalculate() {
        PayrollRun draft = run(HrStatus.PAYROLL_DRAFT);
        when(payrollRepository.findById(1L)).thenReturn(Optional.of(draft));
        when(employeeRepository.listActive()).thenReturn(List.of(employee()));
        AttendanceSummary attendance = attendance();
        when(attendanceRepository.listByPeriod("2026-11")).thenReturn(List.of(attendance));

        PayrollRunView view = service.calculate(1L);

        assertEquals(HrStatus.PAYROLL_CALCULATED, view.getStatus());
        assertEquals(1, view.getEmployeeCount());
        assertEquals(1, view.getPayslips().size());
        assertEquals(0, view.getTotalGross().compareTo(new BigDecimal("11000.00")));
        verify(payrollRepository).savePayslips(any(PayrollRun.class));
        verify(payrollRepository).update(any(PayrollRun.class));
    }

    @Test
    @DisplayName("审批薪资生成计提凭证")
    void shouldApproveWithAccrualVoucher() {
        when(payrollRepository.findById(1L)).thenReturn(Optional.of(run(HrStatus.PAYROLL_CALCULATED)));

        PayrollRunView view = service.approve(1L, 99L);

        assertEquals(HrStatus.PAYROLL_APPROVED, view.getStatus());
        ArgumentCaptor<AutoVoucherCommand> captor = ArgumentCaptor.forClass(AutoVoucherCommand.class);
        verify(autoVoucherAppService).postPayrollAccrual(captor.capture());
        AutoVoucherCommand command = captor.getValue();
        assertEquals(HrStatus.SOURCE_PAYROLL_ACCRUAL, command.getSourceType());
        assertEquals("PR-202611-0001", command.getSourceId());
        assertEquals(LocalDate.of(2026, 11, 30), command.getVoucherDate());
        assertEquals(0, command.getAmount().compareTo(new BigDecimal("11000.00")));
        assertEquals(99L, command.getOperatorId());
    }

    @Test
    @DisplayName("发放薪资生成发放凭证并拆分实发、个税与代扣")
    void shouldPayWithPaymentVoucher() {
        when(payrollRepository.findById(1L)).thenReturn(Optional.of(run(HrStatus.PAYROLL_APPROVED)));

        PayrollRunView view = service.pay(1L, 99L);

        assertEquals(HrStatus.PAYROLL_PAID, view.getStatus());
        ArgumentCaptor<AutoVoucherCommand> captor = ArgumentCaptor.forClass(AutoVoucherCommand.class);
        verify(autoVoucherAppService).postPayrollPayment(captor.capture());
        AutoVoucherCommand command = captor.getValue();
        assertEquals(HrStatus.SOURCE_PAYROLL_PAYMENT, command.getSourceType());
        assertEquals(0, command.getAmount().compareTo(new BigDecimal("11000.00")));
        assertEquals(0, command.getNetAmount().compareTo(new BigDecimal("9035.00")));
        assertEquals(0, command.getTaxAmount().compareTo(new BigDecimal("215.00")));
        assertEquals(0, command.getWithholdingAmount().compareTo(new BigDecimal("1750.00")));
    }

    @Test
    @DisplayName("状态冲突时审批拒绝")
    void shouldRejectApproveOnDraft() {
        when(payrollRepository.findById(1L)).thenReturn(Optional.of(run(HrStatus.PAYROLL_DRAFT)));

        BizException exception = assertThrows(BizException.class, () -> service.approve(1L, 99L));

        assertEquals(ErpErrorCode.HR_STATE_CONFLICT, exception.getCode());
        verify(autoVoucherAppService, never()).postPayrollAccrual(any());
    }

    @Test
    @DisplayName("批次不存在按 404 处理")
    void shouldRejectMissingRun() {
        when(payrollRepository.findById(1L)).thenReturn(Optional.empty());

        BizException exception = assertThrows(BizException.class, () -> service.detail(1L));

        assertEquals(ErpErrorCode.PAYROLL_NOT_FOUND, exception.getCode());
    }

    @Test
    @DisplayName("未关联员工档案时我的工资条拒绝")
    void shouldRejectMyPayslipsWithoutEmployee() {
        when(employeeRepository.findByUserId(99L)).thenReturn(Optional.empty());

        BizException exception = assertThrows(BizException.class, () -> service.myPayslips(99L, new HrQuery()));

        assertEquals(ErpErrorCode.EMPLOYEE_NOT_FOUND, exception.getCode());
    }

    @Test
    @DisplayName("我的工资条按员工分页")
    void shouldPageMyPayslips() {
        Employee employee = employee();
        when(employeeRepository.findByUserId(99L)).thenReturn(Optional.of(employee));
        when(payrollRepository.pageByEmployee("E001", new HrQuery())).thenReturn(List.of(run(HrStatus.PAYROLL_APPROVED)));
        when(payrollRepository.countByEmployee("E001", new HrQuery())).thenReturn(1L);
        var page = service.myPayslips(99L, new HrQuery());

        assertEquals(1, page.getRecords().size());
        assertEquals(1L, page.getTotal());
    }

    /**
     * 构造薪资批次。
     *
     * @param status 状态
     * @return 批次
     */
    private PayrollRun run(String status) {
        PayrollRun run = new PayrollRun();
        run.setId(1L);
        run.setRunNo("PR-202611-0001");
        run.setPeriod("2026-11");
        run.setStatus(status);
        run.setEmployeeCount(1);
        run.setTotalGross(new BigDecimal("11000.00"));
        run.setTotalSocialInsurance(new BigDecimal("1050.00"));
        run.setTotalHousingFund(new BigDecimal("700.00"));
        run.setTotalTax(new BigDecimal("215.00"));
        run.setTotalNet(new BigDecimal("9035.00"));
        return run;
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
        employee.setHireDate(LocalDate.of(2026, 1, 1));
        employee.setStatus(HrStatus.EMPLOYEE_ACTIVE);
        employee.setBaseSalary(new BigDecimal("10000"));
        employee.setAllowance(new BigDecimal("1000"));
        employee.setSocialBase(new BigDecimal("10000"));
        employee.setUserId(99L);
        return employee;
    }

    /**
     * 构造考勤汇总。
     *
     * @return 考勤
     */
    private AttendanceSummary attendance() {
        AttendanceSummary attendance = new AttendanceSummary();
        attendance.setId(2L);
        attendance.setEmployeeNo("E001");
        attendance.setPeriod("2026-11");
        attendance.setWorkDays(new BigDecimal("21.75"));
        attendance.setActualDays(new BigDecimal("21.75"));
        attendance.setLeaveHours(BigDecimal.ZERO);
        attendance.setOvertimeHours(BigDecimal.ZERO);
        attendance.setAbsentDays(BigDecimal.ZERO);
        return attendance;
    }

    /**
     * 构造新建批次命令。
     *
     * @param period 期间
     * @return 命令
     */
    private PayrollCreateCommand command(String period) {
        PayrollCreateCommand command = new PayrollCreateCommand();
        command.setPeriod(period);
        command.setRemark("11 月薪资");
        return command;
    }

    /**
     * 构造薪资参数。
     *
     * @return 参数
     */
    private HrPayrollProperties payrollProperties() {
        HrPayrollProperties properties = new HrPayrollProperties();
        properties.setSocialInsuranceRate(new BigDecimal("0.105"));
        properties.setHousingFundRate(new BigDecimal("0.07"));
        properties.setTaxThreshold(new BigDecimal("5000"));
        properties.setOvertimeMultiplier(new BigDecimal("1.5"));
        properties.setMonthlyWorkDays(new BigDecimal("21.75"));
        properties.setDailyHours(new BigDecimal("8"));
        return properties;
    }
}
