package com.bluewhale.erp.analytics.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.analytics.application.command.ReportDefinitionSaveCommand;
import com.bluewhale.erp.analytics.application.command.ReportScheduleSaveCommand;
import com.bluewhale.erp.analytics.application.service.AnalyticsAppService;
import com.bluewhale.erp.analytics.application.service.ReportAppService;
import com.bluewhale.erp.analytics.application.view.CubeResultView;
import com.bluewhale.erp.analytics.application.view.CubeRowView;
import com.bluewhale.erp.analytics.application.view.ReportDefinitionView;
import com.bluewhale.erp.analytics.application.view.ReportScheduleView;
import com.bluewhale.erp.analytics.domain.model.AnalyticsQuery;
import com.bluewhale.erp.analytics.domain.model.ReportDefinition;
import com.bluewhale.erp.analytics.domain.model.ReportSchedule;
import com.bluewhale.erp.analytics.domain.repository.ReportRepository;
import com.bluewhale.erp.common.ErpErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 报表中心应用服务实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class ReportAppServiceImpl implements ReportAppService {

    private final ReportRepository reportRepository;

    private final AnalyticsAppService analyticsAppService;

    @Override
    public PageResult<ReportDefinitionView> pageDefinitions(AnalyticsQuery query) {
        long total = reportRepository.countDefinitions(query);
        List<ReportDefinitionView> records = reportRepository.pageDefinitions(query).stream()
                .map(this::toDefinitionView).toList();
        return PageResult.of(records, total, query.getCurrent(), query.getSize());
    }

    @Override
    @Transactional
    public ReportDefinitionView createDefinition(ReportDefinitionSaveCommand command) {
        if (reportRepository.findDefinitionByNo(command.getReportNo()).isPresent()) {
            throw ErpErrorCode.exception(ErpErrorCode.REPORT_NO_CONFLICT, "报表编号已存在");
        }
        ReportDefinition definition = toDefinition(command);
        definition.validateForCreate();
        reportRepository.insertDefinition(definition);
        return toDefinitionView(definition);
    }

    @Override
    @Transactional
    public ReportDefinitionView updateDefinition(String reportNo, ReportDefinitionSaveCommand command) {
        ReportDefinition definition = reportRepository.findDefinitionByNo(reportNo)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.REPORT_NOT_FOUND));
        definition.setReportName(command.getReportName());
        definition.setReportType(command.getReportType());
        definition.setDimensionType(command.getDimensionType());
        definition.setMetricType(command.getMetricType());
        definition.setFilterJson(command.getFilterJson());
        definition.setStatus(command.getStatus());
        definition.setRemark(command.getRemark());
        if (command.getOwner() != null) {
            definition.setOwner(command.getOwner());
        }
        definition.validateForCreate();
        reportRepository.updateDefinition(definition);
        return toDefinitionView(definition);
    }

    @Override
    public PageResult<ReportScheduleView> pageSchedules(AnalyticsQuery query) {
        long total = reportRepository.countSchedules(query);
        List<ReportScheduleView> records = reportRepository.pageSchedules(query).stream()
                .map(this::toScheduleView).toList();
        return PageResult.of(records, total, query.getCurrent(), query.getSize());
    }

    @Override
    @Transactional
    public ReportScheduleView createSchedule(ReportScheduleSaveCommand command) {
        if (reportRepository.findScheduleByNo(command.getScheduleNo()).isPresent()) {
            throw ErpErrorCode.exception(ErpErrorCode.SCHEDULE_NO_CONFLICT, "报表计划编号已存在");
        }
        ensureReportExists(command.getReportNo());
        ReportSchedule schedule = toSchedule(command);
        schedule.validateForCreate();
        reportRepository.insertSchedule(schedule);
        return toScheduleView(schedule);
    }

    @Override
    @Transactional
    public ReportScheduleView updateSchedule(String scheduleNo, ReportScheduleSaveCommand command) {
        ReportSchedule schedule = reportRepository.findScheduleByNo(scheduleNo)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.REPORT_SCHEDULE_NOT_FOUND));
        schedule.setReportNo(command.getReportNo());
        schedule.setScheduleName(command.getScheduleName());
        schedule.setCronExpression(command.getCronExpression());
        schedule.setChannel(command.getChannel());
        schedule.setRecipients(command.getRecipients());
        schedule.setStatus(command.getStatus());
        schedule.setRemark(command.getRemark());
        ensureReportExists(command.getReportNo());
        schedule.validateForCreate();
        reportRepository.updateSchedule(schedule);
        return toScheduleView(schedule);
    }

    @Override
    public CubeResultView execute(String reportNo, LocalDate from, LocalDate to) {
        ReportDefinition definition = reportRepository.findDefinitionByNo(reportNo)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.REPORT_NOT_FOUND));
        AnalyticsQuery query = new AnalyticsQuery();
        query.setDimensionType(definition.getDimensionType());
        query.setMetricType(definition.getMetricType());
        query.setFromDate(from);
        query.setToDate(to);
        return analyticsAppService.cube(query);
    }

    @Override
    public String exportCsv(String reportNo, LocalDate from, LocalDate to) {
        CubeResultView result = execute(reportNo, from, to);
        StringBuilder csv = new StringBuilder("\uFEFF维度键,维度名称,指标,指标值,订单数\r\n");
        for (CubeRowView row : result.getRows()) {
            csv.append(csvCell(row.getDimensionKey())).append(',')
                    .append(csvCell(row.getDimensionName())).append(',')
                    .append(result.getMetricType()).append(',')
                    .append(row.getMetricValue() == null ? BigDecimal.ZERO : row.getMetricValue()).append(',')
                    .append(row.getOrderCount()).append("\r\n");
        }
        return csv.toString();
    }

    @Override
    @Transactional
    public ReportScheduleView runSchedule(String scheduleNo) {
        ReportSchedule schedule = reportRepository.findScheduleByNo(scheduleNo)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.REPORT_SCHEDULE_NOT_FOUND));
        ensureReportExists(schedule.getReportNo());
        schedule.markRun(LocalDateTime.now());
        reportRepository.updateSchedule(schedule);
        return toScheduleView(schedule);
    }

    /**
     * 校验报表存在。
     *
     * @param reportNo 报表编号
     */
    private void ensureReportExists(String reportNo) {
        if (reportRepository.findDefinitionByNo(reportNo).isEmpty()) {
            throw ErpErrorCode.exception(ErpErrorCode.REPORT_NOT_FOUND, "引用的报表不存在");
        }
    }

    /**
     * CSV 单元格转义。
     *
     * @param value 原值
     * @return 转义值
     */
    private String csvCell(String value) {
        if (value == null) {
            return "";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    /**
     * 命令转领域定义。
     *
     * @param command 命令
     * @return 定义
     */
    private ReportDefinition toDefinition(ReportDefinitionSaveCommand command) {
        ReportDefinition definition = new ReportDefinition();
        definition.setReportNo(command.getReportNo());
        definition.setReportName(command.getReportName());
        definition.setReportType(command.getReportType());
        definition.setDimensionType(command.getDimensionType());
        definition.setMetricType(command.getMetricType());
        definition.setFilterJson(command.getFilterJson());
        definition.setStatus(command.getStatus());
        definition.setOwner(command.getOwner());
        definition.setRemark(command.getRemark());
        return definition;
    }

    /**
     * 命令转领域计划。
     *
     * @param command 命令
     * @return 计划
     */
    private ReportSchedule toSchedule(ReportScheduleSaveCommand command) {
        ReportSchedule schedule = new ReportSchedule();
        schedule.setScheduleNo(command.getScheduleNo());
        schedule.setReportNo(command.getReportNo());
        schedule.setScheduleName(command.getScheduleName());
        schedule.setCronExpression(command.getCronExpression());
        schedule.setChannel(command.getChannel());
        schedule.setRecipients(command.getRecipients());
        schedule.setStatus(command.getStatus());
        schedule.setRemark(command.getRemark());
        return schedule;
    }

    /**
     * 定义转视图。
     *
     * @param definition 定义
     * @return 视图
     */
    private ReportDefinitionView toDefinitionView(ReportDefinition definition) {
        ReportDefinitionView view = new ReportDefinitionView();
        view.setReportNo(definition.getReportNo());
        view.setReportName(definition.getReportName());
        view.setReportType(definition.getReportType());
        view.setDimensionType(definition.getDimensionType());
        view.setMetricType(definition.getMetricType());
        view.setFilterJson(definition.getFilterJson());
        view.setStatus(definition.getStatus());
        view.setOwner(definition.getOwner());
        view.setRemark(definition.getRemark());
        return view;
    }

    /**
     * 计划转视图。
     *
     * @param schedule 计划
     * @return 视图
     */
    private ReportScheduleView toScheduleView(ReportSchedule schedule) {
        ReportScheduleView view = new ReportScheduleView();
        view.setScheduleNo(schedule.getScheduleNo());
        view.setReportNo(schedule.getReportNo());
        view.setScheduleName(schedule.getScheduleName());
        view.setCronExpression(schedule.getCronExpression());
        view.setChannel(schedule.getChannel());
        view.setRecipients(schedule.getRecipients());
        view.setLastRunTime(schedule.getLastRunTime());
        view.setNextRunTime(schedule.getNextRunTime());
        view.setStatus(schedule.getStatus());
        view.setRemark(schedule.getRemark());
        return view;
    }
}
