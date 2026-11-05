package com.bluewhale.oa.hr.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.hr.application.command.WorkCalendarSaveCommand;
import com.bluewhale.oa.hr.application.view.WorkCalendarView;
import com.bluewhale.oa.hr.domain.model.WorkCalendarQuery;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 工作日历应用服务。
 *
 * <p>无日历记录时默认周一至周五为工作日、标准工时 8 小时。</p>
 *
 * @author BlueWhale
 */
public interface WorkCalendarAppService {

    /**
     * 分页查询工作日历。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<WorkCalendarView> page(WorkCalendarQuery query);

    /**
     * 按日期保存日历（幂等 upsert）。
     *
     * @param date 日期
     * @param command 保存命令
     * @return 保存后的日历
     */
    WorkCalendarView upsert(LocalDate date, WorkCalendarSaveCommand command);

    /**
     * 统计日期区间内的工作日天数（含首尾，整天计 1）。
     *
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 工作日天数
     */
    BigDecimal countWorkdays(LocalDate startDate, LocalDate endDate);

    /**
     * 统计日期区间内的工作日天数（支持半天时段）。
     *
     * @param startDate 开始日期
     * @param startPeriod 开始时段（AM / PM）
     * @param endDate 结束日期
     * @param endPeriod 结束时段（AM / PM）
     * @return 工作日天数
     */
    BigDecimal countWorkdays(LocalDate startDate, String startPeriod, LocalDate endDate, String endPeriod);

    /**
     * 计算某天某时段的工作日系数：非工作日为 0，AM/PM 为 0.5，整天为 1。
     *
     * @param date 日期
     * @param period 时段（AM / PM，传 {@code null} 表示整天）
     * @return 工作日系数
     */
    BigDecimal workdayFraction(LocalDate date, String period);

    /**
     * 判断某天是否工作日。
     *
     * @param date 日期
     * @return 工作日返回 {@code true}
     */
    boolean isWorkday(LocalDate date);
}
