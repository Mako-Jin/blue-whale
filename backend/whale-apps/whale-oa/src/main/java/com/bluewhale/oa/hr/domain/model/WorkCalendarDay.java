package com.bluewhale.oa.hr.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 工作日历值对象。
 *
 * <p>仅承载某一天的工日标记与标准工时；无记录时由应用服务按周一至周五兜底，
 * 因此本对象不感知"默认工作日"规则。</p>
 *
 * @author BlueWhale
 */
@Data
public class WorkCalendarDay {

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

    /**
     * 是否工作日（空值按非工作日处理）。
     *
     * @return 工作日返回 {@code true}
     */
    public boolean isWorkday() {
        return Boolean.TRUE.equals(workday);
    }
}
