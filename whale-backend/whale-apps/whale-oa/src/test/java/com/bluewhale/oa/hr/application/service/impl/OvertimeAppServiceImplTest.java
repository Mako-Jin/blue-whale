package com.bluewhale.oa.hr.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.hr.application.command.OvertimeSaveCommand;
import com.bluewhale.oa.hr.application.command.OvertimeStatusCommand;
import com.bluewhale.oa.hr.application.service.LeaveBalanceAppService;
import com.bluewhale.oa.hr.domain.model.EmployeeProfile;
import com.bluewhale.oa.hr.domain.model.LeaveType;
import com.bluewhale.oa.hr.domain.model.Overtime;
import com.bluewhale.oa.hr.domain.model.OvertimeCompensation;
import com.bluewhale.oa.hr.domain.model.OvertimeStatus;
import com.bluewhale.oa.hr.domain.repository.EmployeeProfileRepository;
import com.bluewhale.oa.hr.domain.repository.LeaveTypeRepository;
import com.bluewhale.oa.hr.domain.repository.OvertimeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link OvertimeAppServiceImpl} 加班闭环单元测试：时长计算、重叠与月上限校验、调休入账幂等。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class OvertimeAppServiceImplTest {

    @Mock
    private OvertimeRepository overtimeRepository;

    @Mock
    private EmployeeProfileRepository employeeProfileRepository;

    @Mock
    private LeaveTypeRepository leaveTypeRepository;

    @Mock
    private LeaveBalanceAppService leaveBalanceAppService;

    private OvertimeAppServiceImpl overtimeAppService;

    /**
     * 手动构造被测服务，月度上限走默认值 36 小时。
     */
    @BeforeEach
    void setUp() {
        overtimeAppService = new OvertimeAppServiceImpl(overtimeRepository, employeeProfileRepository,
                leaveTypeRepository, leaveBalanceAppService);
    }

    @Test
    @DisplayName("create：按起止时间计算时长并落库为待审批")
    void shouldCreatePendingOvertimeWithCalculatedHours() {
        when(overtimeRepository.existsByCode(anyString(), any())).thenReturn(false);
        when(employeeProfileRepository.findByUserId(100L)).thenReturn(Optional.of(profile()));
        when(overtimeRepository.listByApplicantAndDate(anyString(), any(), any())).thenReturn(List.of());
        when(overtimeRepository.sumHours(anyString(), any(), any(), any())).thenReturn(BigDecimal.ZERO);

        overtimeAppService.create(command(LocalTime.of(18, 0), LocalTime.of(21, 30)));

        ArgumentCaptor<Overtime> captor = ArgumentCaptor.forClass(Overtime.class);
        verify(overtimeRepository).insert(captor.capture());
        Overtime saved = captor.getValue();
        assertEquals("OA-OT-202611-100", saved.getCode());
        assertEquals(new BigDecimal("3.5"), saved.getHours());
        assertEquals(OvertimeStatus.PENDING, saved.getStatus());
        assertEquals(OvertimeCompensation.COMP_TIME, saved.getCompensation());
        verifyNoInteractions(leaveBalanceAppService);
    }

    @Test
    @DisplayName("create：同日时段重叠时拒绝")
    void shouldRejectOverlappedPeriod() {
        when(overtimeRepository.existsByCode(anyString(), any())).thenReturn(false);
        when(employeeProfileRepository.findByUserId(100L)).thenReturn(Optional.of(profile()));
        when(overtimeRepository.listByApplicantAndDate(anyString(), any(), any()))
                .thenReturn(List.of(pending(LocalTime.of(18, 0), LocalTime.of(20, 0))));

        BizException exception = assertThrows(BizException.class,
                () -> overtimeAppService.create(command(LocalTime.of(19, 0), LocalTime.of(21, 0))));

        assertEquals(OaErrorCode.OVERTIME_PERIOD_OVERLAP, exception.getCode());
        verify(overtimeRepository, never()).insert(any());
    }

    @Test
    @DisplayName("create：月度累计超过 36 小时时拒绝")
    void shouldRejectWhenMonthlyLimitExceeded() {
        when(overtimeRepository.existsByCode(anyString(), any())).thenReturn(false);
        when(employeeProfileRepository.findByUserId(100L)).thenReturn(Optional.of(profile()));
        when(overtimeRepository.listByApplicantAndDate(anyString(), any(), any())).thenReturn(List.of());
        when(overtimeRepository.sumHours(anyString(), any(), any(), any()))
                .thenReturn(new BigDecimal("35.0"));

        BizException exception = assertThrows(BizException.class,
                () -> overtimeAppService.create(command(LocalTime.of(18, 0), LocalTime.of(20, 0))));

        assertEquals(OaErrorCode.OVERTIME_MONTHLY_LIMIT, exception.getCode());
        verify(overtimeRepository, never()).insert(any());
    }

    @Test
    @DisplayName("updateStatus：审批通过按 8 小时=1 天调入调休，重复同步只入账一次")
    void shouldGrantCompTimeOnceOnApproval() {
        when(overtimeRepository.findById(1L)).thenReturn(Optional.of(pending(
                LocalTime.of(18, 0), LocalTime.of(22, 0))));
        when(leaveTypeRepository.findByCode("COMP_TIME")).thenReturn(Optional.of(compTimeType()));

        overtimeAppService.updateStatus(1L, status("APPROVED"));
        overtimeAppService.updateStatus(1L, status("APPROVED"));

        verify(overtimeRepository, times(1)).update(any());
        verify(leaveBalanceAppService, times(1)).grant(eq(100L), eq(404L), eq(2026),
                eq(new BigDecimal("0.5")), eq("OVERTIME"), eq(1L));
    }

    @Test
    @DisplayName("updateStatus：加班费补偿不产生调休入账")
    void shouldNotGrantWhenCompensationIsPay() {
        Overtime overtime = pending(LocalTime.of(18, 0), LocalTime.of(22, 0));
        overtime.setCompensation(OvertimeCompensation.PAY);
        when(overtimeRepository.findById(1L)).thenReturn(Optional.of(overtime));

        overtimeAppService.updateStatus(1L, status("APPROVED"));

        verifyNoInteractions(leaveBalanceAppService);
    }

    @Test
    @DisplayName("updateStatus：终态不可再次变更")
    void shouldRejectStatusChangeFromTerminal() {
        Overtime overtime = pending(LocalTime.of(18, 0), LocalTime.of(22, 0));
        overtime.setStatus(OvertimeStatus.REJECTED);
        when(overtimeRepository.findById(1L)).thenReturn(Optional.of(overtime));

        BizException exception = assertThrows(BizException.class,
                () -> overtimeAppService.updateStatus(1L, status("APPROVED")));

        assertEquals(OaErrorCode.OVERTIME_STATUS_CONFLICT, exception.getCode());
        verify(overtimeRepository, never()).update(any());
    }

    /**
     * 构造加班保存命令。
     *
     * @param start 开始时间
     * @param end 结束时间
     * @return 保存命令
     */
    private OvertimeSaveCommand command(LocalTime start, LocalTime end) {
        OvertimeSaveCommand command = new OvertimeSaveCommand();
        command.setCode("OA-OT-202611-100");
        command.setApplicant("zhangsan");
        command.setApplicantId(100L);
        command.setOvertimeDate(LocalDate.of(2026, 11, 20));
        command.setStartTime(start);
        command.setEndTime(end);
        command.setCompensation("COMP_TIME");
        command.setProjectName("Whale OA");
        command.setReason("版本发布");
        return command;
    }

    /**
     * 构造状态命令。
     *
     * @param status 目标状态
     * @return 状态命令
     */
    private OvertimeStatusCommand status(String status) {
        OvertimeStatusCommand command = new OvertimeStatusCommand();
        command.setStatus(status);
        return command;
    }

    /**
     * 构造待审批加班申请。
     *
     * @param start 开始时间
     * @param end 结束时间
     * @return 加班申请
     */
    private Overtime pending(LocalTime start, LocalTime end) {
        Overtime overtime = new Overtime();
        overtime.setId(1L);
        overtime.setCode("OA-OT-202611-100");
        overtime.setApplicant("zhangsan");
        overtime.setApplicantId(100L);
        overtime.setOvertimeDate(LocalDate.of(2026, 11, 20));
        overtime.setStartTime(start);
        overtime.setEndTime(end);
        overtime.setHours(BigDecimal.valueOf(Duration.between(start, end).toMinutes())
                .divide(BigDecimal.valueOf(60), 1, RoundingMode.HALF_UP));
        overtime.setCompensation(OvertimeCompensation.COMP_TIME);
        overtime.setStatus(OvertimeStatus.PENDING);
        return overtime;
    }

    /**
     * 构造调休假期类型。
     *
     * @return 假期类型
     */
    private LeaveType compTimeType() {
        LeaveType type = new LeaveType();
        type.setId(404L);
        type.setCode("COMP_TIME");
        type.setName("调休");
        return type;
    }

    /**
     * 构造员工档案。
     *
     * @return 员工档案
     */
    private EmployeeProfile profile() {
        EmployeeProfile profile = new EmployeeProfile();
        profile.setUserId(100L);
        return profile;
    }
}
