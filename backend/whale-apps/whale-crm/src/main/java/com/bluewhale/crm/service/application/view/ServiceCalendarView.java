package com.bluewhale.crm.service.application.view;

import com.bluewhale.crm.service.domain.model.ServiceCalendar;
import lombok.Data;

/**
 * 工作日历视图。
 *
 * @author BlueWhale
 */
@Data
public class ServiceCalendarView {

    /** 日历编码 */
    private String calendarCode;

    /** 上班时间 HH:mm */
    private String workdayStart;

    /** 下班时间 HH:mm */
    private String workdayEnd;

    /** 午休分钟数 */
    private Integer breakMinutes;

    /** 工作日（ISO 1–7） */
    private String weekdays;

    /** 是否启用 */
    private Boolean enabled;

    /**
     * 由领域对象构建视图。
     *
     * @param calendar 日历
     * @return 视图
     */
    public static ServiceCalendarView from(ServiceCalendar calendar) {
        ServiceCalendarView view = new ServiceCalendarView();
        view.setCalendarCode(calendar.getCalendarCode());
        view.setWorkdayStart(calendar.getWorkdayStart() == null ? null : calendar.getWorkdayStart().toString());
        view.setWorkdayEnd(calendar.getWorkdayEnd() == null ? null : calendar.getWorkdayEnd().toString());
        view.setBreakMinutes(calendar.getBreakMinutes());
        view.setWeekdays(calendar.getWeekdays());
        view.setEnabled(calendar.getEnabled());
        return view;
    }
}
