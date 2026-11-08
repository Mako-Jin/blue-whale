package com.bluewhale.oa.attendance.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.oa.attendance.application.command.AttendanceCommands;
import com.bluewhale.oa.attendance.application.service.AttendanceDailyAppService;
import com.bluewhale.oa.attendance.domain.model.AttendanceCorrection;
import com.bluewhale.oa.attendance.domain.model.AttendanceRecord;
import com.bluewhale.oa.attendance.domain.model.AttendanceRule;
import com.bluewhale.oa.attendance.domain.model.AttendanceStatus;
import com.bluewhale.oa.attendance.domain.repository.AttendanceConfigRepository;
import com.bluewhale.oa.attendance.domain.repository.AttendanceCorrectionRepository;
import com.bluewhale.oa.attendance.domain.repository.AttendanceRecordRepository;
import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.common.application.service.SequenceAppService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AttendanceCorrectionAppServiceImpl} 单元测试：补卡窗口 / 月上限 / 幂等与状态机。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class AttendanceCorrectionAppServiceImplTest {

    private static final Long USER_ID = 100L;

    @Mock
    private AttendanceCorrectionRepository correctionRepository;

    @Mock
    private AttendanceRecordRepository recordRepository;

    @Mock
    private AttendanceConfigRepository configRepository;

    @Mock
    private AttendanceDailyAppService dailyAppService;

    @Mock
    private SequenceAppService sequenceAppService;

    private AttendanceCorrectionAppServiceImpl correctionAppService;

    /**
     * 构造被测服务。
     */
    @BeforeEach
    void setUp() {
        correctionAppService = new AttendanceCorrectionAppServiceImpl(
                correctionRepository, recordRepository, configRepository, dailyAppService, sequenceAppService);
    }

    @Test
    @DisplayName("create：补卡类型非 IN/OUT 时拒绝")
    void shouldRejectInvalidPunchType() {
        AttendanceCommands.CorrectionSaveCommand command = command(
                LocalDate.now().minusDays(1), "PATCH");

        BizException ex = assertThrows(BizException.class,
                () -> correctionAppService.create(USER_ID, command));

        assertEquals(OaErrorCode.ATT_INVALID, ex.getCode());
        verify(correctionRepository, never()).save(any());
    }

    @Test
    @DisplayName("create：补卡时间与补卡日期不一致时拒绝")
    void shouldRejectPunchTimeNotOnWorkDate() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        AttendanceCommands.CorrectionSaveCommand command = command(yesterday, "IN");
        command.setPunchTime(LocalDate.now().minusDays(2).atTime(9, 0));

        BizException ex = assertThrows(BizException.class,
                () -> correctionAppService.create(USER_ID, command));

        assertEquals(OaErrorCode.ATT_INVALID, ex.getCode());
    }

    @Test
    @DisplayName("create：未来日期拒绝")
    void shouldRejectFutureWorkDate() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        AttendanceCommands.CorrectionSaveCommand command = command(tomorrow, "IN");

        BizException ex = assertThrows(BizException.class,
                () -> correctionAppService.create(USER_ID, command));

        assertEquals(OaErrorCode.ATT_INVALID, ex.getCode());
    }

    @Test
    @DisplayName("create：超出默认 7 天补卡窗口拒绝")
    void shouldRejectBeyondDefaultWindow() {
        LocalDate eightDaysAgo = LocalDate.now().minusDays(8);
        AttendanceCommands.CorrectionSaveCommand command = command(eightDaysAgo, "IN");

        BizException ex = assertThrows(BizException.class,
                () -> correctionAppService.create(USER_ID, command));

        assertEquals(OaErrorCode.ATT_INVALID, ex.getCode());
    }

    @Test
    @DisplayName("create：该时间已有打卡记录时拒绝（无需补卡）")
    void shouldRejectWhenPunchRecordExists() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        AttendanceCommands.CorrectionSaveCommand command = command(yesterday, "IN");
        stubNoRule();
        when(recordRepository.exists(eq(USER_ID), eq(command.getPunchTime()), eq("IN"))).thenReturn(true);

        BizException ex = assertThrows(BizException.class,
                () -> correctionAppService.create(USER_ID, command));

        assertEquals(OaErrorCode.ATT_RECORD_DUPLICATE, ex.getCode());
    }

    @Test
    @DisplayName("create：同日同类型补卡正在审批中时拒绝")
    void shouldRejectWhenPendingCorrectionExists() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        AttendanceCommands.CorrectionSaveCommand command = command(yesterday, "IN");
        stubNoRule();
        when(recordRepository.exists(eq(USER_ID), any(LocalDateTime.class), eq("IN"))).thenReturn(false);
        when(correctionRepository.findPending(eq(USER_ID), eq(yesterday), eq("IN")))
                .thenReturn(Optional.of(new AttendanceCorrection()));

        BizException ex = assertThrows(BizException.class,
                () -> correctionAppService.create(USER_ID, command));

        assertEquals(OaErrorCode.ATT_CORRECTION_LIMIT, ex.getCode());
    }

    @Test
    @DisplayName("create：当月补卡次数达到上限（默认 3 次）时拒绝")
    void shouldRejectWhenMonthlyLimitReached() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        AttendanceCommands.CorrectionSaveCommand command = command(yesterday, "IN");
        stubNoRule();
        when(recordRepository.exists(eq(USER_ID), any(LocalDateTime.class), eq("IN"))).thenReturn(false);
        when(correctionRepository.findPending(eq(USER_ID), eq(yesterday), eq("IN"))).thenReturn(Optional.empty());
        when(correctionRepository.countByMonth(eq(USER_ID), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(3L);

        BizException ex = assertThrows(BizException.class,
                () -> correctionAppService.create(USER_ID, command));

        assertEquals(OaErrorCode.ATT_CORRECTION_LIMIT, ex.getCode());
    }

    @Test
    @DisplayName("create：窗口与上限内成功，保存 PENDING 并生成单号")
    void shouldCreatePendingCorrectionWithinLimits() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        AttendanceCommands.CorrectionSaveCommand command = command(yesterday, "IN");
        stubNoRule();
        when(recordRepository.exists(eq(USER_ID), any(LocalDateTime.class), eq("IN"))).thenReturn(false);
        when(correctionRepository.findPending(eq(USER_ID), eq(yesterday), eq("IN"))).thenReturn(Optional.empty());
        when(correctionRepository.countByMonth(eq(USER_ID), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(0L);
        when(sequenceAppService.nextCode("OA_ATT_CORRECTION", "ATT")).thenReturn("ATT-20261108-0001");

        correctionAppService.create(USER_ID, command);

        ArgumentCaptor<AttendanceCorrection> captor = ArgumentCaptor.forClass(AttendanceCorrection.class);
        verify(correctionRepository).save(captor.capture());
        AttendanceCorrection saved = captor.getValue();
        assertEquals("ATT-20261108-0001", saved.getCode());
        assertEquals(USER_ID, saved.getUserId());
        assertEquals(AttendanceStatus.CORRECTION_PENDING, saved.getStatus());
    }

    @Test
    @DisplayName("create：考勤组规则窗口生效（10 天窗口内允许）")
    void shouldUseGroupRuleWindow() {
        LocalDate nineDaysAgo = LocalDate.now().minusDays(9);
        AttendanceCommands.CorrectionSaveCommand command = command(nineDaysAgo, "OUT");
        AttendanceRule rule = new AttendanceRule();
        rule.setMaxCorrectionsPerMonth(3);
        rule.setCorrectionWindowDays(10);
        when(configRepository.findGroupByUserId(USER_ID)).thenReturn(Optional.empty());
        when(configRepository.findDefaultRule()).thenReturn(Optional.of(rule));
        when(recordRepository.exists(eq(USER_ID), any(LocalDateTime.class), eq("OUT"))).thenReturn(false);
        when(correctionRepository.findPending(eq(USER_ID), eq(nineDaysAgo), eq("OUT"))).thenReturn(Optional.empty());
        when(correctionRepository.countByMonth(eq(USER_ID), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(0L);
        when(sequenceAppService.nextCode(anyString(), anyString())).thenReturn("ATT-0002");

        correctionAppService.create(USER_ID, command);

        verify(correctionRepository).save(any(AttendanceCorrection.class));
    }

    @Test
    @DisplayName("updateStatus：补卡不存在时拒绝")
    void shouldRejectUpdateWhenNotFound() {
        when(correctionRepository.findById(9L)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class,
                () -> correctionAppService.updateStatus(9L, statusCommand("APPROVED"), "admin"));

        assertEquals(OaErrorCode.ATT_CORRECTION_NOT_FOUND, ex.getCode());
    }

    @Test
    @DisplayName("updateStatus：目标状态非法时拒绝")
    void shouldRejectInvalidTargetStatus() {
        when(correctionRepository.findById(1L)).thenReturn(Optional.of(correction(AttendanceStatus.CORRECTION_PENDING)));

        BizException ex = assertThrows(BizException.class,
                () -> correctionAppService.updateStatus(1L, statusCommand("DONE"), "admin"));

        assertEquals(OaErrorCode.ATT_INVALID, ex.getCode());
    }

    @Test
    @DisplayName("updateStatus：已终态且目标状态不同时冲突；目标相同时幂等返回")
    void shouldConflictOrReturnIdempotentWhenFinalStatus() {
        when(correctionRepository.findById(1L))
                .thenReturn(Optional.of(correction(AttendanceStatus.CORRECTION_REJECTED)));

        BizException conflict = assertThrows(BizException.class,
                () -> correctionAppService.updateStatus(1L, statusCommand("APPROVED"), "admin"));
        assertEquals(OaErrorCode.ATT_CORRECTION_STATUS_CONFLICT, conflict.getCode());

        correctionAppService.updateStatus(1L, statusCommand("REJECTED"), "admin");
        verify(correctionRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateStatus：审批通过写入补卡记录并触发当日重算")
    void shouldApplyApprovedAndRecalculate() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        AttendanceCorrection pending = correction(AttendanceStatus.CORRECTION_PENDING);
        pending.setUserId(USER_ID);
        pending.setWorkDate(yesterday);
        pending.setPunchTime(yesterday.atTime(9, 0));
        pending.setPunchType(AttendanceStatus.PUNCH_IN);
        pending.setCode("ATT-0003");
        when(correctionRepository.findById(1L)).thenReturn(Optional.of(pending));
        when(recordRepository.exists(eq(USER_ID), any(LocalDateTime.class), eq(AttendanceStatus.PUNCH_IN)))
                .thenReturn(false);

        correctionAppService.updateStatus(1L, statusCommand("APPROVED"), "admin");

        ArgumentCaptor<AttendanceRecord> recordCaptor = ArgumentCaptor.forClass(AttendanceRecord.class);
        verify(recordRepository).save(recordCaptor.capture());
        assertEquals(1, recordCaptor.getValue().getCorrection());
        ArgumentCaptor<AttendanceCommands.RecalculateCommand> recalcCaptor =
                ArgumentCaptor.forClass(AttendanceCommands.RecalculateCommand.class);
        verify(dailyAppService).recalculate(recalcCaptor.capture());
        assertEquals(USER_ID, recalcCaptor.getValue().getUserId());
        assertEquals(yesterday, recalcCaptor.getValue().getDateFrom());
        assertEquals(yesterday, recalcCaptor.getValue().getDateTo());
        verify(correctionRepository, times(2)).save(pending);
        assertEquals(AttendanceStatus.CORRECTION_APPROVED, pending.getStatus());
    }

    @Test
    @DisplayName("syncApprovalResult：非 PENDING 或不存在时幂等返回")
    void shouldIgnoreSyncWhenNotPending() {
        when(correctionRepository.findById(1L)).thenReturn(Optional.empty());
        correctionAppService.syncApprovalResult(1L, "APPROVED");
        verify(correctionRepository, never()).save(any());

        when(correctionRepository.findById(2L))
                .thenReturn(Optional.of(correction(AttendanceStatus.CORRECTION_APPROVED)));
        correctionAppService.syncApprovalResult(2L, "REJECTED");
        verify(correctionRepository, never()).save(any());
    }

    @Test
    @DisplayName("syncApprovalResult：REJECTED 置状态，未知结果不变更")
    void shouldSyncRejectedAndIgnoreUnknownResult() {
        AttendanceCorrection pending = correction(AttendanceStatus.CORRECTION_PENDING);
        when(correctionRepository.findById(3L)).thenReturn(Optional.of(pending));
        correctionAppService.syncApprovalResult(3L, "rejected");
        assertEquals(AttendanceStatus.CORRECTION_REJECTED, pending.getStatus());

        AttendanceCorrection untouched = correction(AttendanceStatus.CORRECTION_PENDING);
        when(correctionRepository.findById(4L)).thenReturn(Optional.of(untouched));
        correctionAppService.syncApprovalResult(4L, "WITHDRAWN");
        assertEquals(AttendanceStatus.CORRECTION_PENDING, untouched.getStatus());
        verify(correctionRepository, never()).save(untouched);
    }

    /**
     * 未配置考勤组与默认规则的通用桩。
     */
    private void stubNoRule() {
        lenient().when(configRepository.findGroupByUserId(anyLong())).thenReturn(Optional.empty());
        lenient().when(configRepository.findDefaultRule()).thenReturn(Optional.empty());
    }

    /**
     * 构造补卡保存命令。
     *
     * @param workDate 补卡日期
     * @param punchType 打卡类型
     * @return 命令
     */
    private static AttendanceCommands.CorrectionSaveCommand command(LocalDate workDate, String punchType) {
        AttendanceCommands.CorrectionSaveCommand command = new AttendanceCommands.CorrectionSaveCommand();
        command.setWorkDate(workDate);
        command.setPunchTime(workDate.atTime(9, 0));
        command.setPunchType(punchType);
        command.setReason("忘记打卡");
        command.setUserName("张三");
        return command;
    }

    /**
     * 构造状态变更命令。
     *
     * @param status 目标状态
     * @return 命令
     */
    private static AttendanceCommands.CorrectionStatusCommand statusCommand(String status) {
        AttendanceCommands.CorrectionStatusCommand command = new AttendanceCommands.CorrectionStatusCommand();
        command.setStatus(status);
        command.setRemark("处理备注");
        return command;
    }

    /**
     * 构造补卡实体。
     *
     * @param status 状态
     * @return 补卡实体
     */
    private static AttendanceCorrection correction(String status) {
        AttendanceCorrection correction = new AttendanceCorrection();
        correction.setId(1L);
        correction.setStatus(status);
        return correction;
    }
}
