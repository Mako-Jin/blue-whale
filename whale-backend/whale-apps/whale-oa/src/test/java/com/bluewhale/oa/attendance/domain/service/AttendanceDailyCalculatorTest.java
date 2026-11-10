package com.bluewhale.oa.attendance.domain.service;

import com.bluewhale.oa.attendance.domain.model.AttendanceShift;
import com.bluewhale.oa.attendance.domain.model.AttendanceStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 考勤日汇总计算器单元测试。
 *
 * @author BlueWhale
 */
class AttendanceDailyCalculatorTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 6);

    private static AttendanceShift shift() {
        AttendanceShift shift = new AttendanceShift();
        shift.setCode("STD");
        shift.setWorkStart(LocalTime.of(9, 0));
        shift.setWorkEnd(LocalTime.of(18, 0));
        shift.setFlexMinutes(15);
        return shift;
    }

    @Test
    @DisplayName("正常打卡在弹性范围内记正常")
    void normalWithinFlex() {
        var result = AttendanceDailyCalculator.compute(shift(),
                LocalDateTime.of(DATE, LocalTime.of(9, 10)),
                LocalDateTime.of(DATE, LocalTime.of(17, 50)),
                false, false, DATE);
        assertEquals(AttendanceStatus.DAILY_NORMAL, result.getStatus());
        assertEquals(0, result.getLateMinutes());
        assertEquals(0, result.getEarlyMinutes());
        assertEquals(0, result.getAbsent());
    }

    @Test
    @DisplayName("超过弹性上班记迟到分钟")
    void lateBeyondFlex() {
        var result = AttendanceDailyCalculator.compute(shift(),
                LocalDateTime.of(DATE, LocalTime.of(9, 30)),
                LocalDateTime.of(DATE, LocalTime.of(18, 5)),
                false, false, DATE);
        assertEquals(AttendanceStatus.DAILY_LATE, result.getStatus());
        assertEquals(15, result.getLateMinutes());
        assertEquals(0, result.getEarlyMinutes());
    }

    @Test
    @DisplayName("超过弹性下班记早退分钟")
    void earlyBeyondFlex() {
        var result = AttendanceDailyCalculator.compute(shift(),
                LocalDateTime.of(DATE, LocalTime.of(8, 55)),
                LocalDateTime.of(DATE, LocalTime.of(17, 40)),
                false, false, DATE);
        assertEquals(AttendanceStatus.DAILY_EARLY, result.getStatus());
        assertEquals(0, result.getLateMinutes());
        assertEquals(5, result.getEarlyMinutes());
    }

    @Test
    @DisplayName("迟到且早退")
    void lateAndEarly() {
        var result = AttendanceDailyCalculator.compute(shift(),
                LocalDateTime.of(DATE, LocalTime.of(9, 40)),
                LocalDateTime.of(DATE, LocalTime.of(17, 0)),
                false, false, DATE);
        assertEquals(AttendanceStatus.DAILY_LATE_AND_EARLY, result.getStatus());
        assertEquals(25, result.getLateMinutes());
        assertEquals(45, result.getEarlyMinutes());
    }

    @Test
    @DisplayName("无任何打卡记缺卡")
    void absentWithoutPunch() {
        var result = AttendanceDailyCalculator.compute(shift(), null, null, false, false, DATE);
        assertEquals(AttendanceStatus.DAILY_ABSENT, result.getStatus());
        assertEquals(1, result.getAbsent());
        assertEquals("无打卡记录", result.getRemark());
    }

    @Test
    @DisplayName("缺少下班打卡记缺卡并给出提示")
    void absentWhenMissingOut() {
        var result = AttendanceDailyCalculator.compute(shift(),
                LocalDateTime.of(DATE, LocalTime.of(9, 0)), null, false, false, DATE);
        assertEquals(AttendanceStatus.DAILY_ABSENT, result.getStatus());
        assertEquals(1, result.getAbsent());
        assertEquals("缺少下班打卡", result.getRemark());
        assertEquals(0, result.getLateMinutes());
    }

    @Test
    @DisplayName("已批准请假优先记请假")
    void leaveOverridesPunch() {
        var result = AttendanceDailyCalculator.compute(shift(), null, null, true, false, DATE);
        assertEquals(AttendanceStatus.DAILY_LEAVE, result.getStatus());
        assertEquals("请假", result.getRemark());
    }

    @Test
    @DisplayName("已批准出差优先记出差")
    void officialOverridesPunch() {
        var result = AttendanceDailyCalculator.compute(shift(),
                LocalDateTime.of(DATE, LocalTime.of(9, 0)),
                LocalDateTime.of(DATE, LocalTime.of(18, 0)),
                false, true, DATE);
        assertEquals(AttendanceStatus.DAILY_OFFICIAL, result.getStatus());
        assertEquals("出差", result.getRemark());
    }
}
