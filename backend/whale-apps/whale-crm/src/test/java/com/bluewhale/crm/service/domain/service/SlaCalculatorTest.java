package com.bluewhale.crm.service.domain.service;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.crm.common.CrmErrorCode;
import com.bluewhale.crm.service.domain.model.ServiceCalendar;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link SlaCalculator} 工作时段推进单元测试（7×8，含午休）。
 *
 * @author BlueWhale
 */
class SlaCalculatorTest {

    /** 2026-11-06 为周五 */
    private static final LocalDateTime FRIDAY = LocalDateTime.of(2026, 11, 6, 0, 0);

    @Test
    @DisplayName("同日推进：2 个工作小时不跨午休")
    void shouldStayBeforeLunch() {
        LocalDateTime deadline = SlaCalculator.deadline(FRIDAY.withHour(9), 2, weekdayCalendar());

        assertEquals(FRIDAY.withHour(11), deadline);
    }

    @Test
    @DisplayName("同日推进：4 个工作小时跨午休后顺延至 14:00")
    void shouldSkipLunchBreak() {
        LocalDateTime deadline = SlaCalculator.deadline(FRIDAY.withHour(9), 4, weekdayCalendar());

        assertEquals(FRIDAY.withHour(14), deadline);
    }

    @Test
    @DisplayName("午休期间起算：从 13:00 继续计时")
    void shouldStartAfterLunchWhenInsideBreak() {
        LocalDateTime deadline = SlaCalculator.deadline(FRIDAY.withHour(12).withMinute(30), 1, weekdayCalendar());

        assertEquals(FRIDAY.withHour(14), deadline);
    }

    @Test
    @DisplayName("跨日推进：周五 17:00 起 3 小时顺延至周一 11:00")
    void shouldSkipWeekend() {
        LocalDateTime fridayEvening = FRIDAY.withHour(17);

        LocalDateTime deadline = SlaCalculator.deadline(fridayEvening, 3, weekdayCalendar());

        assertEquals(LocalDateTime.of(2026, 11, 9, 11, 0), deadline);
    }

    @Test
    @DisplayName("整日推进：周五 10:00 起 8 小时用尽当日工时后进入下一工作日")
    void shouldRollToNextWorkdayAfterFullDay() {
        LocalDateTime deadline = SlaCalculator.deadline(FRIDAY.withHour(10), 8, weekdayCalendar());

        assertEquals(LocalDateTime.of(2026, 11, 9, 10, 0), deadline);
    }

    @Test
    @DisplayName("七天工作制：周五 17:00 起 3 小时顺延至周六 11:00")
    void shouldUseWeeklyCalendar() {
        ServiceCalendar calendar = weekdayCalendar();
        calendar.setWeekdays("1,2,3,4,5,6,7");

        LocalDateTime deadline = SlaCalculator.deadline(FRIDAY.withHour(17), 3, calendar);

        assertEquals(LocalDateTime.of(2026, 11, 7, 11, 0), deadline);
    }

    @Test
    @DisplayName("非工作日起算：周六 10:00 顺延至周一 10:00")
    void shouldAlignWeekendStartToMonday() {
        LocalDateTime deadline = SlaCalculator.deadline(LocalDateTime.of(2026, 11, 7, 10, 0), 1, weekdayCalendar());

        assertEquals(LocalDateTime.of(2026, 11, 9, 10, 0), deadline);
    }

    @Test
    @DisplayName("无午休日历按整段工作时段推进")
    void shouldNotDeductBreakWhenZero() {
        ServiceCalendar calendar = weekdayCalendar();
        calendar.setBreakMinutes(0);

        LocalDateTime deadline = SlaCalculator.deadline(FRIDAY.withHour(9), 9, calendar);

        assertEquals(FRIDAY.withHour(18), deadline);
    }

    @Test
    @DisplayName("午休窗口不足时从当日末端扣除，与 dailyMinutes 一致")
    void shouldDeductBreakAtDayEndWhenNoonNotFits() {
        ServiceCalendar calendar = weekdayCalendar();
        calendar.setWorkdayEnd(LocalTime.of(12, 30));

        LocalDateTime deadline = SlaCalculator.deadline(FRIDAY.withHour(9), 3, calendar);

        assertEquals(LocalDateTime.of(2026, 11, 9, 9, 30), deadline);
        assertEquals(150, calendar.dailyMinutes());
    }

    @Test
    @DisplayName("非法时限：小于等于 0 时拒绝")
    void shouldRejectNonPositiveHours() {
        BizException exception = assertThrows(BizException.class,
                () -> SlaCalculator.deadline(FRIDAY.withHour(9), 0, weekdayCalendar()));

        assertEquals(CrmErrorCode.TICKET_SLA_UNAVAILABLE, exception.getCode());
    }

    /**
     * 构造 09:00–18:00、午休 60 分钟、周一至周五的工作日历。
     *
     * @return 工作日历
     */
    private static ServiceCalendar weekdayCalendar() {
        ServiceCalendar calendar = new ServiceCalendar();
        calendar.setCalendarCode("DEFAULT");
        calendar.setWorkdayStart(LocalTime.of(9, 0));
        calendar.setWorkdayEnd(LocalTime.of(18, 0));
        calendar.setBreakMinutes(60);
        calendar.setWeekdays("1,2,3,4,5");
        calendar.setEnabled(true);
        return calendar;
    }
}
