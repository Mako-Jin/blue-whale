package com.bluewhale.erp.analytics.domain.repository;

import com.bluewhale.erp.analytics.domain.model.AnalyticsQuery;
import com.bluewhale.erp.analytics.domain.model.ReportDefinition;
import com.bluewhale.erp.analytics.domain.model.ReportSchedule;

import java.util.List;
import java.util.Optional;

/**
 * 报表定义与定时计划仓储。
 *
 * @author BlueWhale
 */
public interface ReportRepository {

    /**
     * 分页查询报表定义。
     *
     * @param query 查询条件
     * @return 报表定义
     */
    List<ReportDefinition> pageDefinitions(AnalyticsQuery query);

    /**
     * 统计报表定义。
     *
     * @param query 查询条件
     * @return 总条数
     */
    long countDefinitions(AnalyticsQuery query);

    /**
     * 按报表编号查询。
     *
     * @param reportNo 报表编号
     * @return 报表定义
     */
    Optional<ReportDefinition> findDefinitionByNo(String reportNo);

    /**
     * 新增报表定义。
     *
     * @param definition 报表定义
     */
    void insertDefinition(ReportDefinition definition);

    /**
     * 更新报表定义。
     *
     * @param definition 报表定义
     */
    void updateDefinition(ReportDefinition definition);

    /**
     * 分页查询定时计划。
     *
     * @param query 查询条件
     * @return 定时计划
     */
    List<ReportSchedule> pageSchedules(AnalyticsQuery query);

    /**
     * 统计定时计划。
     *
     * @param query 查询条件
     * @return 总条数
     */
    long countSchedules(AnalyticsQuery query);

    /**
     * 按计划编号查询。
     *
     * @param scheduleNo 计划编号
     * @return 定时计划
     */
    Optional<ReportSchedule> findScheduleByNo(String scheduleNo);

    /**
     * 新增定时计划。
     *
     * @param schedule 定时计划
     */
    void insertSchedule(ReportSchedule schedule);

    /**
     * 更新定时计划。
     *
     * @param schedule 定时计划
     */
    void updateSchedule(ReportSchedule schedule);
}
