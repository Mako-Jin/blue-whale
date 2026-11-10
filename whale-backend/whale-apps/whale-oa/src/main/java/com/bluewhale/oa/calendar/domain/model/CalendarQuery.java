package com.bluewhale.oa.calendar.domain.model;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 日程分页查询。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CalendarQuery extends PageQuery {

    /** 关键字（标题 / 地点 / 描述） */
    private String keyword;

    /** 可见性 */
    private String visibility;

    /** 日期起（含） */
    private LocalDate dateFrom;

    /** 日期止（含） */
    private LocalDate dateTo;

    /** 当前查看人用户ID */
    private Long userId;
}
