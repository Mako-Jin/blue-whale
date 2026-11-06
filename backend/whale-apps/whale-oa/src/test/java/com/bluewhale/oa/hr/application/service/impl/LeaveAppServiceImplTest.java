package com.bluewhale.oa.hr.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.common.application.command.FileRefRegisterCommand;
import com.bluewhale.oa.common.application.service.FileRefAppService;
import com.bluewhale.oa.common.domain.model.FileRef;
import com.bluewhale.oa.common.domain.repository.FileRefRepository;
import com.bluewhale.oa.hr.application.command.LeaveSaveCommand;
import com.bluewhale.oa.hr.application.command.LeaveStatusCommand;
import com.bluewhale.oa.hr.application.service.LeaveBalanceAppService;
import com.bluewhale.oa.hr.application.service.WorkCalendarAppService;
import com.bluewhale.oa.hr.domain.model.EmployeeProfile;
import com.bluewhale.oa.hr.domain.model.Leave;
import com.bluewhale.oa.hr.domain.model.LeaveQuery;
import com.bluewhale.oa.hr.domain.model.LeaveStatus;
import com.bluewhale.oa.hr.domain.model.LeaveType;
import com.bluewhale.oa.hr.domain.model.LeaveTypeUnit;
import com.bluewhale.oa.hr.domain.repository.EmployeeProfileRepository;
import com.bluewhale.oa.hr.domain.repository.LeaveRepository;
import com.bluewhale.oa.hr.domain.repository.LeaveTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link LeaveAppServiceImpl} 请假闭环单元测试：工作日计算、重叠校验、额度冻结与状态幂等。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class LeaveAppServiceImplTest {

    @Mock
    private LeaveRepository leaveRepository;

    @Mock
    private LeaveTypeRepository leaveTypeRepository;

    @Mock
    private EmployeeProfileRepository employeeProfileRepository;

    @Mock
    private WorkCalendarAppService workCalendarAppService;

    @Mock
    private LeaveBalanceAppService leaveBalanceAppService;

    @Mock
    private FileRefAppService fileRefAppService;

    @Mock
    private FileRefRepository fileRefRepository;

    private LeaveAppServiceImpl leaveAppService;

    /**
     * 手动构造被测服务，避免 Spring 容器启动。
     */
    @BeforeEach
    void setUp() {
        leaveAppService = new LeaveAppServiceImpl(leaveRepository, leaveTypeRepository,
                employeeProfileRepository, workCalendarAppService, leaveBalanceAppService,
                fileRefAppService, fileRefRepository);
    }

    @Test
    @DisplayName("create：跨工作日按整天计算天数并冻结额度")
    void shouldCreatePendingLeaveWithWorkdays() {
        when(leaveRepository.existsByCode(anyString(), any())).thenReturn(false);
        when(leaveTypeRepository.findByCode("ANNUAL")).thenReturn(Optional.of(annualType()));
        when(employeeProfileRepository.findByUserId(100L)).thenReturn(Optional.of(profile()));
        when(leaveRepository.listOverlapping(anyString(), anyString(), any(), any(), any()))
                .thenReturn(List.of());
        when(workCalendarAppService.workdayFraction(any(), any())).thenReturn(BigDecimal.ONE);

        leaveAppService.create(request());

        ArgumentCaptor<Leave> captor = ArgumentCaptor.forClass(Leave.class);
        verify(leaveRepository).insert(captor.capture());
        Leave saved = captor.getValue();
        assertEquals("OA-LV-202610-100", saved.getCode());
        assertEquals(LeaveStatus.PENDING, saved.getStatus());
        assertEquals(new BigDecimal("2.0"), saved.getDays());
        assertEquals(100L, saved.getApplicantId());
        assertEquals(100L, saved.getLeaveTypeId());
        verify(leaveBalanceAppService).freeze(eq(100L), eq(100L), eq(2026),
                eq(new BigDecimal("2.0")), eq(false), eq("LEAVE"), any());
    }

    @Test
    @DisplayName("create：同一天 AM → AM 计半天并冻结 0.5")
    void shouldCalculateHalfDay() {
        LeaveSaveCommand command = request();
        command.setStartDate(LocalDate.of(2026, 10, 26));
        command.setEndDate(LocalDate.of(2026, 10, 26));
        command.setStartPeriod("AM");
        command.setEndPeriod("AM");
        when(leaveRepository.existsByCode(anyString(), any())).thenReturn(false);
        when(leaveTypeRepository.findByCode("ANNUAL")).thenReturn(Optional.of(annualType()));
        when(employeeProfileRepository.findByUserId(100L)).thenReturn(Optional.of(profile()));
        when(leaveRepository.listOverlapping(anyString(), anyString(), any(), any(), any()))
                .thenReturn(List.of());
        when(workCalendarAppService.workdayFraction(any(), eq("AM")))
                .thenReturn(new BigDecimal("0.5"));

        leaveAppService.create(command);

        ArgumentCaptor<Leave> captor = ArgumentCaptor.forClass(Leave.class);
        verify(leaveRepository).insert(captor.capture());
        assertEquals(new BigDecimal("0.5"), captor.getValue().getDays());
        verify(leaveBalanceAppService).freeze(eq(100L), eq(100L), eq(2026),
                eq(new BigDecimal("0.5")), eq(false), eq("LEAVE"), any());
    }

    @Test
    @DisplayName("create：跨三天工作日按 3.0 天计算")
    void shouldCalculateCrossDayWorkdays() {
        LeaveSaveCommand command = request();
        command.setEndDate(LocalDate.of(2026, 10, 28));
        when(leaveRepository.existsByCode(anyString(), any())).thenReturn(false);
        when(leaveTypeRepository.findByCode("ANNUAL")).thenReturn(Optional.of(annualType()));
        when(employeeProfileRepository.findByUserId(100L)).thenReturn(Optional.of(profile()));
        when(leaveRepository.listOverlapping(anyString(), anyString(), any(), any(), any()))
                .thenReturn(List.of());
        when(workCalendarAppService.workdayFraction(any(), any())).thenReturn(BigDecimal.ONE);

        leaveAppService.create(command);

        ArgumentCaptor<Leave> captor = ArgumentCaptor.forClass(Leave.class);
        verify(leaveRepository).insert(captor.capture());
        assertEquals(new BigDecimal("3.0"), captor.getValue().getDays());
    }

    @Test
    @DisplayName("create：区间内无工作日时拒绝")
    void shouldRejectWhenNoWorkday() {
        when(leaveRepository.existsByCode(anyString(), any())).thenReturn(false);
        when(leaveTypeRepository.findByCode("ANNUAL")).thenReturn(Optional.of(annualType()));
        when(employeeProfileRepository.findByUserId(100L)).thenReturn(Optional.of(profile()));
        when(workCalendarAppService.workdayFraction(any(), any())).thenReturn(BigDecimal.ZERO);

        BizException exception = assertThrows(BizException.class, () -> leaveAppService.create(request()));

        assertEquals(OaErrorCode.LEAVE_INVALID, exception.getCode());
        verify(leaveRepository, never()).insert(any());
        verifyNoInteractions(leaveBalanceAppService);
    }

    @Test
    @DisplayName("create：同一申请人同类型时间重叠时拒绝")
    void shouldRejectOverlappingLeave() {
        when(leaveRepository.existsByCode(anyString(), any())).thenReturn(false);
        when(leaveTypeRepository.findByCode("ANNUAL")).thenReturn(Optional.of(annualType()));
        when(employeeProfileRepository.findByUserId(100L)).thenReturn(Optional.of(profile()));
        when(workCalendarAppService.workdayFraction(any(), any())).thenReturn(BigDecimal.ONE);
        when(leaveRepository.listOverlapping(anyString(), anyString(), any(), any(), any()))
                .thenReturn(List.of(pendingLeave()));

        BizException exception = assertThrows(BizException.class, () -> leaveAppService.create(request()));

        assertEquals(OaErrorCode.LEAVE_PERIOD_OVERLAP, exception.getCode());
        verify(leaveRepository, never()).insert(any());
        verifyNoInteractions(leaveBalanceAppService);
    }

    @Test
    @DisplayName("create：申请人档案不存在时拒绝")
    void shouldRejectMissingApplicantProfile() {
        when(leaveRepository.existsByCode(anyString(), any())).thenReturn(false);
        when(leaveTypeRepository.findByCode("ANNUAL")).thenReturn(Optional.of(annualType()));

        BizException exception = assertThrows(BizException.class, () -> leaveAppService.create(request()));

        assertEquals(OaErrorCode.EMPLOYEE_PROFILE_NOT_FOUND, exception.getCode());
        verify(leaveRepository, never()).insert(any());
    }

    @Test
    @DisplayName("create：单号重复时拒绝")
    void shouldRejectDuplicateCode() {
        when(leaveRepository.existsByCode(anyString(), any())).thenReturn(true);

        BizException exception = assertThrows(BizException.class, () -> leaveAppService.create(request()));

        assertEquals(OaErrorCode.LEAVE_INVALID, exception.getCode());
        verify(leaveRepository, never()).insert(any());
    }

    @Test
    @DisplayName("create：结束日期早于开始日期时拒绝")
    void shouldRejectInvalidDateRange() {
        LeaveSaveCommand command = request();
        command.setEndDate(LocalDate.of(2026, 10, 20));

        BizException exception = assertThrows(BizException.class, () -> leaveAppService.create(command));

        assertEquals(OaErrorCode.LEAVE_INVALID, exception.getCode());
        verify(leaveRepository, never()).existsByCode(any(), any());
    }

    @Test
    @DisplayName("create：要求附件的假期类型缺附件时拒绝")
    void shouldRejectMissingAttachmentForSickLeave() {
        LeaveSaveCommand command = sickRequest();
        when(leaveRepository.existsByCode(anyString(), any())).thenReturn(false);
        when(leaveTypeRepository.findByCode("SICK")).thenReturn(Optional.of(sickType()));
        when(employeeProfileRepository.findByUserId(100L)).thenReturn(Optional.of(profile()));
        when(workCalendarAppService.workdayFraction(any(), any())).thenReturn(BigDecimal.ONE);
        when(leaveRepository.listOverlapping(anyString(), anyString(), any(), any(), any()))
                .thenReturn(List.of());

        BizException exception = assertThrows(BizException.class, () -> leaveAppService.create(command));

        assertEquals(OaErrorCode.LEAVE_INVALID, exception.getCode());
        verifyNoInteractions(fileRefAppService);
    }

    @Test
    @DisplayName("create：附件超过 5 个时拒绝")
    void shouldRejectTooManyAttachments() {
        LeaveSaveCommand command = sickRequest();
        command.setFileIds(List.of(1L, 2L, 3L, 4L, 5L, 6L));
        when(leaveRepository.existsByCode(anyString(), any())).thenReturn(false);
        when(leaveTypeRepository.findByCode("SICK")).thenReturn(Optional.of(sickType()));
        when(employeeProfileRepository.findByUserId(100L)).thenReturn(Optional.of(profile()));
        when(workCalendarAppService.workdayFraction(any(), any())).thenReturn(BigDecimal.ONE);
        when(leaveRepository.listOverlapping(anyString(), anyString(), any(), any(), any()))
                .thenReturn(List.of());

        BizException exception = assertThrows(BizException.class, () -> leaveAppService.create(command));

        assertEquals(OaErrorCode.LEAVE_INVALID, exception.getCode());
        verifyNoInteractions(fileRefAppService);
    }

    @Test
    @DisplayName("create：要求附件的类型携带附件时登记引用")
    void shouldRegisterAttachment() {
        LeaveSaveCommand command = sickRequest();
        command.setFileIds(List.of(11L));
        when(leaveRepository.existsByCode(anyString(), any())).thenReturn(false);
        when(leaveTypeRepository.findByCode("SICK")).thenReturn(Optional.of(sickType()));
        when(employeeProfileRepository.findByUserId(100L)).thenReturn(Optional.of(profile()));
        when(workCalendarAppService.workdayFraction(any(), any())).thenReturn(BigDecimal.ONE);
        when(leaveRepository.listOverlapping(anyString(), anyString(), any(), any(), any()))
                .thenReturn(List.of());

        leaveAppService.create(command);

        ArgumentCaptor<FileRefRegisterCommand> captor = ArgumentCaptor.forClass(FileRefRegisterCommand.class);
        verify(fileRefAppService).register(captor.capture());
        assertEquals("LEAVE", captor.getValue().getBizType());
        assertEquals(11L, captor.getValue().getFileId());
    }

    @Test
    @DisplayName("update：只保留指定附件并登记新增文件")
    void shouldReplaceAttachmentsOnUpdate() {
        LeaveSaveCommand command = sickRequest();
        command.setFileIds(List.of(9L));
        command.setKeepAttachmentIds(List.of(1L));
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(pendingLeave()));
        when(leaveRepository.existsByCode(anyString(), eq(1L))).thenReturn(false);
        when(leaveTypeRepository.findByCode("SICK")).thenReturn(Optional.of(sickType()));
        when(employeeProfileRepository.findByUserId(100L)).thenReturn(Optional.of(profile()));
        when(workCalendarAppService.workdayFraction(any(), any())).thenReturn(BigDecimal.ONE);
        when(leaveRepository.listOverlapping(anyString(), anyString(), any(), any(), any()))
                .thenReturn(List.of());
        when(fileRefRepository.listByBiz("LEAVE", 1L)).thenReturn(List.of(
                fileRef(1L, 7L), fileRef(2L, 8L)));

        leaveAppService.update(1L, command);

        verify(fileRefRepository).deleteById(2L);
        ArgumentCaptor<FileRefRegisterCommand> captor = ArgumentCaptor.forClass(FileRefRegisterCommand.class);
        verify(fileRefAppService).register(captor.capture());
        assertEquals(9L, captor.getValue().getFileId());
    }

    @Test
    @DisplayName("updateStatus：审批通过扣减额度")
    void shouldDeductWhenApproved() {
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(pendingLeave()));

        LeaveStatusCommand command = statusCommand("APPROVED");
        leaveAppService.updateStatus(1L, command);

        ArgumentCaptor<Leave> captor = ArgumentCaptor.forClass(Leave.class);
        verify(leaveRepository).update(captor.capture());
        assertEquals(LeaveStatus.APPROVED, captor.getValue().getStatus());
        verify(leaveBalanceAppService).deduct(100L, 100L, 2026, new BigDecimal("2.0"), "LEAVE", 1L);
    }

    @Test
    @DisplayName("updateStatus：驳回释放冻结额度")
    void shouldReleaseWhenRejected() {
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(pendingLeave()));

        leaveAppService.updateStatus(1L, statusCommand("REJECTED"));

        verify(leaveBalanceAppService).release(100L, 100L, 2026, new BigDecimal("2.0"), "LEAVE", 1L);
        verify(leaveBalanceAppService, never()).deduct(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("updateStatus：重复同步同一终态幂等返回")
    void shouldBeIdempotentForSameStatus() {
        Leave leave = pendingLeave();
        leave.setStatus(LeaveStatus.APPROVED);
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));

        leaveAppService.updateStatus(1L, statusCommand("APPROVED"));

        verify(leaveRepository, never()).update(any());
        verifyNoInteractions(leaveBalanceAppService);
    }

    @Test
    @DisplayName("updateStatus：终态不可变更为其他状态")
    void shouldRejectIllegalTransition() {
        Leave leave = pendingLeave();
        leave.setStatus(LeaveStatus.APPROVED);
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));

        BizException exception = assertThrows(BizException.class,
                () -> leaveAppService.updateStatus(1L, statusCommand("REJECTED")));

        assertEquals(OaErrorCode.LEAVE_STATUS_CONFLICT, exception.getCode());
        verify(leaveRepository, never()).update(any());
    }

    @Test
    @DisplayName("syncApprovalResult：映射审批结果并幂等回写")
    void shouldSyncApprovalResult() {
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(pendingLeave()));

        leaveAppService.syncApprovalResult(1L, "APPROVED");

        ArgumentCaptor<Leave> captor = ArgumentCaptor.forClass(Leave.class);
        verify(leaveRepository).update(captor.capture());
        assertEquals(LeaveStatus.APPROVED, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("page：透传分页参数并映射视图对象")
    void shouldPageLeaves() {
        LeaveQuery query = new LeaveQuery();
        when(leaveRepository.page(query)).thenReturn(List.of(pendingLeave()));
        when(leaveRepository.count(query)).thenReturn(1L);

        var result = leaveAppService.page(query);

        assertEquals(1L, result.getTotal());
        assertEquals("OA-LV-202610-100", result.getRecords().getFirst().getCode());
    }

    /**
     * 构造新增命令。
     *
     * @return 新增命令
     */
    private static LeaveSaveCommand request() {
        LeaveSaveCommand command = new LeaveSaveCommand();
        command.setCode("OA-LV-202610-100");
        command.setApplicant("admin");
        command.setApplicantId(100L);
        command.setLeaveType("ANNUAL");
        command.setStartDate(LocalDate.of(2026, 10, 26));
        command.setEndDate(LocalDate.of(2026, 10, 27));
        command.setStartPeriod("AM");
        command.setEndPeriod("PM");
        command.setDays(new BigDecimal("2.0"));
        command.setReason("年假");
        return command;
    }

    /**
     * 构造病假新增命令。
     *
     * @return 新增命令
     */
    private static LeaveSaveCommand sickRequest() {
        LeaveSaveCommand command = request();
        command.setLeaveType("SICK");
        return command;
    }

    /**
     * 构造要求附件的病假类型。
     *
     * @return 假期类型
     */
    private static LeaveType sickType() {
        LeaveType leaveType = annualType();
        leaveType.setCode("SICK");
        leaveType.setName("病假");
        leaveType.setRequiresAttachment(Boolean.TRUE);
        return leaveType;
    }

    /**
     * 构造文件引用。
     *
     * @param id 引用ID
     * @param fileId 文件ID
     * @return 文件引用
     */
    private static FileRef fileRef(Long id, Long fileId) {
        FileRef fileRef = new FileRef();
        fileRef.setId(id);
        fileRef.setFileId(fileId);
        fileRef.setFileName("attachment-" + fileId + ".pdf");
        return fileRef;
    }

    /**
     * 构造状态命令。
     *
     * @param status 目标状态
     * @return 状态命令
     */
    private static LeaveStatusCommand statusCommand(String status) {
        LeaveStatusCommand command = new LeaveStatusCommand();
        command.setStatus(status);
        return command;
    }

    /**
     * 构造启用年假类型。
     *
     * @return 假期类型
     */
    private static LeaveType annualType() {
        LeaveType leaveType = new LeaveType();
        leaveType.setId(100L);
        leaveType.setCode("ANNUAL");
        leaveType.setName("年假");
        leaveType.setUnit(LeaveTypeUnit.DAY);
        leaveType.setAnnualQuota(new BigDecimal("5.0"));
        leaveType.setMinUnit(new BigDecimal("0.5"));
        leaveType.setAllowNegative(Boolean.FALSE);
        leaveType.setRequiresAttachment(Boolean.FALSE);
        leaveType.setEnabled(Boolean.TRUE);
        leaveType.setSortNo(1);
        return leaveType;
    }

    /**
     * 构造员工档案。
     *
     * @return 员工档案
     */
    private static EmployeeProfile profile() {
        EmployeeProfile profile = new EmployeeProfile();
        profile.setId(10L);
        profile.setUserId(100L);
        profile.setEmploymentStatus(com.bluewhale.oa.hr.domain.model.EmploymentStatus.ACTIVE);
        return profile;
    }

    /**
     * 构造待审批申请。
     *
     * @return 申请领域模型
     */
    private static Leave pendingLeave() {
        Leave leave = new Leave();
        leave.setId(1L);
        leave.setCode("OA-LV-202610-100");
        leave.setApplicant("admin");
        leave.setApplicantId(100L);
        leave.setLeaveType("ANNUAL");
        leave.setLeaveTypeId(100L);
        leave.setStartDate(LocalDate.of(2026, 10, 26));
        leave.setStartPeriod("AM");
        leave.setEndDate(LocalDate.of(2026, 10, 27));
        leave.setEndPeriod("PM");
        leave.setDays(new BigDecimal("2.0"));
        leave.setStatus(LeaveStatus.PENDING);
        return leave;
    }
}
