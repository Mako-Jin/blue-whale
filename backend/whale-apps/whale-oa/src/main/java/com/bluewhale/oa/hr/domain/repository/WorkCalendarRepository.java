package com.bluewhale.oa.hr.domain.repository;

import com.bluewhale.oa.hr.domain.model.WorkCalendarDay;
import com.bluewhale.oa.hr.domain.model.WorkCalendarQuery;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 工作日历仓储。
 *
 * @author BlueWhale
 */
public interface WorkCalendarRepository {

    /**
     * 分页查询日历。
     *
     * @param query 查询条件
     * @return 当前页日历
     */
    List<WorkCalendarDay> page(WorkCalendarQuery query);

    /**
     * 统计日历数量。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(WorkCalendarQuery query);

    /**
     * 按日期查询日历。
     *
     * @param calendarDate 日期
     * @return 日历
     */
    Optional<WorkCalendarDay> findByDate(LocalDate calendarDate);

    /**
     * 查询日期区间内的日历记录（含首尾）。
     *
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 日历列表
     */
    List<WorkCalendarDay> listByDateRange(LocalDate startDate, LocalDate endDate);

    /**
     * 新增日历。
     *
     * @param day 日历
     */
    void insert(WorkCalendarDay day);

    /**
     * 按日期更新日历。
     *
     * @param day 日历
     */
    void updateByDate(WorkCalendarDay day);
}
