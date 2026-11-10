package com.bluewhale.oa.hr.domain.model;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 工作日历分页查询条件。
 *
 * <p>{@code yearMonth} 与 {@code startDate/endDate} 二者取其一：
 * 传年月时按自然月展开，传区间时按区间过滤。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WorkCalendarQuery extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 年月（yyyy-MM） */
    private String yearMonth;

    /** 开始日期 */
    private LocalDate startDate;

    /** 结束日期 */
    private LocalDate endDate;

    /** 是否工作日 */
    private Boolean workday;
}
