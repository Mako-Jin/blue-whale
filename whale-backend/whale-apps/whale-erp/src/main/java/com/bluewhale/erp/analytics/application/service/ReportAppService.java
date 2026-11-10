package com.bluewhale.erp.analytics.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.analytics.application.command.ReportDefinitionSaveCommand;
import com.bluewhale.erp.analytics.application.command.ReportScheduleSaveCommand;
import com.bluewhale.erp.analytics.application.view.CubeResultView;
import com.bluewhale.erp.analytics.application.view.ReportDefinitionView;
import com.bluewhale.erp.analytics.application.view.ReportScheduleView;
import com.bluewhale.erp.analytics.domain.model.AnalyticsQuery;

import java.time.LocalDate;

/**
 * 报表中心应用服务。
 *
 * @author BlueWhale
 */
public interface ReportAppService {

    /**
     * 报表定义分页。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<ReportDefinitionView> pageDefinitions(AnalyticsQuery query);

    /**
     * 新增报表定义。
     *
     * @param command 保存命令
     * @return 报表定义
     */
    ReportDefinitionView createDefinition(ReportDefinitionSaveCommand command);

    /**
     * 编辑报表定义。
     *
     * @param reportNo 报表编号
     * @param command 保存命令
     * @return 报表定义
     */
    ReportDefinitionView updateDefinition(String reportNo, ReportDefinitionSaveCommand command);

    /**
     * 报表计划分页。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<ReportScheduleView> pageSchedules(AnalyticsQuery query);

    /**
     * 新增报表计划。
     *
     * @param command 保存命令
     * @return 报表计划
     */
    ReportScheduleView createSchedule(ReportScheduleSaveCommand command);

    /**
     * 编辑报表计划。
     *
     * @param scheduleNo 计划编号
     * @param command 保存命令
     * @return 报表计划
     */
    ReportScheduleView updateSchedule(String scheduleNo, ReportScheduleSaveCommand command);

    /**
     * 执行报表（返回多维分析结果）。
     *
     * @param reportNo 报表编号
     * @param from 日期起
     * @param to 日期止
     * @return 分析结果
     */
    CubeResultView execute(String reportNo, LocalDate from, LocalDate to);

    /**
     * 导出报表 CSV（受权限码控制）。
     *
     * @param reportNo 报表编号
     * @param from 日期起
     * @param to 日期止
     * @return CSV 文本
     */
    String exportCsv(String reportNo, LocalDate from, LocalDate to);

    /**
     * 手动执行一次定时计划。
     *
     * @param scheduleNo 计划编号
     * @return 报表计划
     */
    ReportScheduleView runSchedule(String scheduleNo);
}
