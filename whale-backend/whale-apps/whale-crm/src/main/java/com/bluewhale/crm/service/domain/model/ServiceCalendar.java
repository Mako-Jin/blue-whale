package com.bluewhale.crm.service.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;
import lombok.Data;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 售后服务工作日历（D7：7×8 工作时段，不引入节假日表）。
 *
 * <p>{@code weekdays} 使用 ISO 星期序号（1=周一 … 7=周日），逗号分隔；默认 1–7 全周可用。</p>
 *
 * @author BlueWhale
 */
@Data
public class ServiceCalendar {

    /** 主键 */
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** 日历编码 */
    private String calendarCode;

    /** 工作日开始时间 */
    private LocalTime workdayStart;

    /** 工作日结束时间 */
    private LocalTime workdayEnd;

    /** 午休分钟数 */
    private Integer breakMinutes;

    /** 工作日（ISO 1–7，逗号分隔） */
    private String weekdays;

    /** 是否启用 */
    private Boolean enabled;

    /**
     * 判断指定日期是否为工作日。
     *
     * @param date 日期
     * @return 是否工作日
     */
    public boolean isWorkday(LocalDate date) {
        return workdaySet().contains(date.getDayOfWeek().getValue());
    }

    /**
     * 计算单个工作日的有效工时（分钟）。
     *
     * @return 有效分钟数
     */
    public long dailyMinutes() {
        if (workdayStart == null || workdayEnd == null) {
            throw CrmErrorCode.exception(CrmErrorCode.TICKET_SLA_UNAVAILABLE, "工作日历缺少上下班时间");
        }
        long total = Duration.between(workdayStart, workdayEnd).toMinutes();
        long breaks = breakMinutes == null ? 0 : Math.max(0, breakMinutes);
        long minutes = total - breaks;
        if (minutes <= 0) {
            throw CrmErrorCode.exception(CrmErrorCode.TICKET_SLA_UNAVAILABLE, "工作日历有效工时必须大于 0");
        }
        return minutes;
    }

    /**
     * 解析工作日集合。
     *
     * @return ISO 星期序号集合
     */
    public Set<Integer> workdaySet() {
        if (weekdays == null || weekdays.isBlank()) {
            return Set.of(1, 2, 3, 4, 5, 6, 7);
        }
        return Arrays.stream(weekdays.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .map(item -> Integer.parseInt(item))
                .collect(Collectors.toSet());
    }

    /**
     * 规范化工作日文本。
     *
     * @param value 原始文本
     * @return 逗号分隔的 ISO 星期序号
     */
    public static String normalizeWeekdays(String value) {
        if (value == null || value.isBlank()) {
            return "1,2,3,4,5,6,7";
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .map(Integer::parseInt)
                .filter(item -> item >= 1 && item <= 7)
                .distinct()
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }
}
