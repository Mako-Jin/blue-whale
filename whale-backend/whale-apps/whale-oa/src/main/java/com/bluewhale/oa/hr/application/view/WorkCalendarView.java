package com.bluewhale.oa.hr.application.view;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 工作日历视图。
 *
 * @author BlueWhale
 */
@Data
public class WorkCalendarView implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 日历日期 */
    private LocalDate calendarDate;

    /** 是否工作日 */
    private Boolean workday;

    /** 当日标准工时 */
    private BigDecimal workHours;

    /** 节假日名称 */
    private String holidayName;

    /** 备注 */
    private String remark;
}
