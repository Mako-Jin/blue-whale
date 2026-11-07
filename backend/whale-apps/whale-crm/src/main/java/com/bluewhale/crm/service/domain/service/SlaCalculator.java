package com.bluewhale.crm.service.domain.service;

import com.bluewhale.crm.common.CrmErrorCode;
import com.bluewhale.crm.service.domain.model.ServiceCalendar;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * SLA 时限计算器。
 *
 * <p>按工作日历逐日推进工作分钟：跳过非工作日与午休，跨日顺延；不引入节假日表（D7）。
 * 午休按 12:00 起算（不足一个完整午休窗口时从当日末端扣除），保证单日有效工时与
 * {@link ServiceCalendar#dailyMinutes()} 一致。计算不依赖 Spring，便于独立单测。</p>
 *
 * @author BlueWhale
 */
public final class SlaCalculator {

    /** 最大推进天数，防止日历配置异常导致死循环 */
    private static final int MAX_DAYS = 3660;

    /**
     * 私有构造器：工具类禁止实例化。
     */
    private SlaCalculator() {
    }

    /**
     * 计算工作小时数后的截止时间。
     *
     * @param start 起算时间
     * @param hours 工作小时数
     * @param calendar 工作日历
     * @return 截止时间
     */
    public static LocalDateTime deadline(LocalDateTime start, long hours, ServiceCalendar calendar) {
        if (start == null) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "SLA 起算时间不能为空");
        }
        if (hours <= 0) {
            throw CrmErrorCode.exception(CrmErrorCode.TICKET_SLA_UNAVAILABLE, "SLA 时限必须大于 0");
        }
        long remaining = hours * 60L;
        LocalDateTime cursor = align(start, calendar);
        for (int day = 0; day < MAX_DAYS; day++) {
            for (LocalDateTime[] segment : segments(cursor.toLocalDate(), calendar)) {
                LocalDateTime segmentStart = segment[0].isBefore(cursor) ? cursor : segment[0];
                if (!segmentStart.isBefore(segment[1])) {
                    continue;
                }
                long available = Duration.between(segmentStart, segment[1]).toMinutes();
                if (remaining <= available) {
                    return segmentStart.plusMinutes(remaining);
                }
                remaining -= available;
            }
            cursor = align(cursor.toLocalDate().plusDays(1).atStartOfDay(), calendar);
        }
        throw CrmErrorCode.exception(CrmErrorCode.TICKET_SLA_UNAVAILABLE, "SLA 计算超出最大天数");
    }

    /**
     * 拆分单个工作日的有效工作时段（扣除午休）。
     *
     * @param date 日期
     * @param calendar 工作日历
     * @return 按时间顺序排列的时段
     */
    private static List<LocalDateTime[]> segments(LocalDate date, ServiceCalendar calendar) {
        LocalDateTime workStart = date.atTime(calendar.getWorkdayStart());
        LocalDateTime workEnd = date.atTime(calendar.getWorkdayEnd());
        long breakMinutes = calendar.getBreakMinutes() == null ? 0L : Math.max(0, calendar.getBreakMinutes());
        List<LocalDateTime[]> segments = new ArrayList<>(2);
        if (breakMinutes <= 0 || Duration.between(workStart, workEnd).toMinutes() <= breakMinutes) {
            addSegment(segments, workStart, workEnd);
            return segments;
        }
        LocalDateTime lunchStart = date.atTime(LocalTime.NOON);
        LocalDateTime lunchEnd = lunchStart.plusMinutes(breakMinutes);
        if (!lunchStart.isBefore(workStart) && !lunchEnd.isAfter(workEnd)) {
            addSegment(segments, workStart, lunchStart);
            addSegment(segments, lunchEnd, workEnd);
            return segments;
        }
        addSegment(segments, workStart, workEnd.minusMinutes(breakMinutes));
        return segments;
    }

    /**
     * 追加工时大于 0 的时段。
     *
     * @param segments 时段列表
     * @param start 开始
     * @param end 结束
     */
    private static void addSegment(List<LocalDateTime[]> segments, LocalDateTime start, LocalDateTime end) {
        if (start.isBefore(end)) {
            segments.add(new LocalDateTime[]{start, end});
        }
    }

    /**
     * 将时间对齐到下一个有效工作时刻。
     *
     * @param moment 原始时间
     * @param calendar 工作日历
     * @return 对齐后的时间
     */
    private static LocalDateTime align(LocalDateTime moment, ServiceCalendar calendar) {
        LocalDateTime cursor = moment;
        for (int day = 0; day < MAX_DAYS; day++) {
            LocalDate date = cursor.toLocalDate();
            if (calendar.isWorkday(date)) {
                LocalTime start = calendar.getWorkdayStart();
                LocalTime end = calendar.getWorkdayEnd();
                LocalDateTime windowStart = date.atTime(start);
                if (!cursor.isBefore(windowStart) && cursor.isBefore(date.atTime(end))) {
                    return cursor;
                }
                if (cursor.isBefore(windowStart)) {
                    return windowStart;
                }
            }
            cursor = date.plusDays(1).atStartOfDay();
        }
        throw CrmErrorCode.exception(CrmErrorCode.TICKET_SLA_UNAVAILABLE, "工作日历无可用工作日");
    }
}
