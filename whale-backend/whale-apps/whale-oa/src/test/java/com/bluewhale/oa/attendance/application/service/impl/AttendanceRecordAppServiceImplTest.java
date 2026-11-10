package com.bluewhale.oa.attendance.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.oa.attendance.application.command.AttendanceCommands;
import com.bluewhale.oa.attendance.application.service.AttendanceDailyAppService;
import com.bluewhale.oa.attendance.domain.model.AttendanceRecord;
import com.bluewhale.oa.attendance.domain.model.AttendanceStatus;
import com.bluewhale.oa.attendance.domain.repository.AttendanceRecordRepository;
import com.bluewhale.oa.common.OaErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AttendanceRecordAppServiceImpl} 单元测试：打卡校验与当日重算联动。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class AttendanceRecordAppServiceImplTest {

    private static final Long USER_ID = 100L;

    @Mock
    private AttendanceRecordRepository recordRepository;

    @Mock
    private AttendanceDailyAppService dailyAppService;

    private AttendanceRecordAppServiceImpl recordAppService;

    /**
     * 构造被测服务。
     */
    @BeforeEach
    void setUp() {
        recordAppService = new AttendanceRecordAppServiceImpl(recordRepository, dailyAppService);
    }

    @Test
    @DisplayName("punch：打卡类型非 IN/OUT 时拒绝")
    void shouldRejectInvalidPunchType() {
        AttendanceCommands.PunchCommand command = punch("PATCH", null);

        BizException ex = assertThrows(BizException.class,
                () -> recordAppService.punch(USER_ID, command));

        assertEquals(OaErrorCode.ATT_INVALID, ex.getCode());
        verify(recordRepository, never()).save(any());
    }

    @Test
    @DisplayName("punch：打卡来源非法时拒绝")
    void shouldRejectInvalidSource() {
        AttendanceCommands.PunchCommand command = punch(AttendanceStatus.PUNCH_IN, "SMS");

        BizException ex = assertThrows(BizException.class,
                () -> recordAppService.punch(USER_ID, command));

        assertEquals(OaErrorCode.ATT_INVALID, ex.getCode());
    }

    @Test
    @DisplayName("punch：同一时间同类型重复打卡时拒绝")
    void shouldRejectDuplicatePunch() {
        LocalDateTime punchTime = LocalDateTime.now().withNano(0);
        AttendanceCommands.PunchCommand command = punch(AttendanceStatus.PUNCH_IN, null);
        command.setPunchTime(punchTime);
        when(recordRepository.exists(eq(USER_ID), eq(punchTime), eq(AttendanceStatus.PUNCH_IN))).thenReturn(true);

        BizException ex = assertThrows(BizException.class,
                () -> recordAppService.punch(USER_ID, command));

        assertEquals(OaErrorCode.ATT_RECORD_DUPLICATE, ex.getCode());
        verify(recordRepository, never()).save(any());
    }

    @Test
    @DisplayName("punch：成功保存（来源缺省 WEB、correction=0）并触发当日重算")
    void shouldSaveAndTriggerDailyRecalculate() {
        LocalDate today = LocalDate.now();
        AttendanceCommands.PunchCommand command = punch(AttendanceStatus.PUNCH_IN, null);
        command.setPunchTime(today.atTime(8, 55));
        command.setUserName("李四");
        when(recordRepository.exists(eq(USER_ID), any(LocalDateTime.class), eq(AttendanceStatus.PUNCH_IN)))
                .thenReturn(false);

        recordAppService.punch(USER_ID, command);

        ArgumentCaptor<AttendanceRecord> captor = ArgumentCaptor.forClass(AttendanceRecord.class);
        verify(recordRepository).save(captor.capture());
        AttendanceRecord saved = captor.getValue();
        assertEquals(USER_ID, saved.getUserId());
        assertEquals(AttendanceStatus.SOURCE_WEB, saved.getSource());
        assertEquals(0, saved.getCorrection());

        ArgumentCaptor<AttendanceCommands.RecalculateCommand> recalcCaptor =
                ArgumentCaptor.forClass(AttendanceCommands.RecalculateCommand.class);
        verify(dailyAppService).recalculate(recalcCaptor.capture());
        assertEquals(USER_ID, recalcCaptor.getValue().getUserId());
        assertEquals(today, recalcCaptor.getValue().getDateFrom());
        assertEquals(today, recalcCaptor.getValue().getDateTo());
    }

    @Test
    @DisplayName("punch：设备来源与定位信息完整落库")
    void shouldSaveDeviceSourceWithLocation() {
        AttendanceCommands.PunchCommand command = punch(AttendanceStatus.PUNCH_OUT, AttendanceStatus.SOURCE_DEVICE);
        command.setPunchTime(LocalDate.now().atTime(18, 5));
        command.setLatitude(new BigDecimal("31.2304"));
        command.setLongitude(new BigDecimal("121.4737"));
        command.setDeviceCode("DEV-001");
        when(recordRepository.exists(eq(USER_ID), any(LocalDateTime.class), eq(AttendanceStatus.PUNCH_OUT)))
                .thenReturn(false);

        recordAppService.punch(USER_ID, command);

        ArgumentCaptor<AttendanceRecord> captor = ArgumentCaptor.forClass(AttendanceRecord.class);
        verify(recordRepository).save(captor.capture());
        assertEquals(AttendanceStatus.SOURCE_DEVICE, captor.getValue().getSource());
        assertEquals("DEV-001", captor.getValue().getDeviceCode());
        assertEquals(new BigDecimal("31.2304"), captor.getValue().getLatitude());
    }

    /**
     * 构造打卡命令。
     *
     * @param punchType 打卡类型
     * @param source 来源（{@code null} 表示缺省）
     * @return 命令
     */
    private static AttendanceCommands.PunchCommand punch(String punchType, String source) {
        AttendanceCommands.PunchCommand command = new AttendanceCommands.PunchCommand();
        command.setPunchType(punchType);
        command.setSource(source);
        return command;
    }
}
