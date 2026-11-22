package com.bluewhale.erp.analytics.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.erp.analytics.application.command.ReportDefinitionSaveCommand;
import com.bluewhale.erp.analytics.application.command.ReportScheduleSaveCommand;
import com.bluewhale.erp.analytics.application.service.ReportAppService;
import com.bluewhale.erp.analytics.application.view.CubeResultView;
import com.bluewhale.erp.analytics.application.view.ReportDefinitionView;
import com.bluewhale.erp.analytics.application.view.ReportScheduleView;
import com.bluewhale.erp.analytics.domain.model.AnalyticsQuery;
import com.bluewhale.erp.interfaces.support.PageParams;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 报表中心接口：定义、计划、执行与导出。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/erp/v{version}/bi/report", version = "1")
@RequiredArgsConstructor
public class AnalyticsReportController {

    private final ReportAppService reportAppService;

    /**
     * 报表定义分页。
     *
     * @param query 查询条件
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @return 分页结果
     */
    @GetMapping("/definition/page")
    @RequirePermission("sys:erp:bi:report:list")
    public Result<PageResult<ReportDefinitionView>> pageDefinitions(
            @ModelAttribute AnalyticsQuery query,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(reportAppService.pageDefinitions(query));
    }

    /**
     * 新增报表定义。
     *
     * @param command 保存命令
     * @return 报表定义
     */
    @PostMapping("/definition")
    @RequirePermission("sys:erp:bi:report:save")
    @AuditLog(operation = "新增报表定义", targetType = "erp_bi_report_def", type = OperateType.CREATE)
    public Result<ReportDefinitionView> createDefinition(@RequestBody ReportDefinitionSaveCommand command) {
        return Result.ok(reportAppService.createDefinition(command));
    }

    /**
     * 编辑报表定义。
     *
     * @param reportNo 报表编号
     * @param command 保存命令
     * @return 报表定义
     */
    @PutMapping("/definition/{reportNo}")
    @RequirePermission("sys:erp:bi:report:save")
    @AuditLog(operation = "编辑报表定义", targetType = "erp_bi_report_def", type = OperateType.UPDATE)
    public Result<ReportDefinitionView> updateDefinition(@PathVariable String reportNo,
                                                         @RequestBody ReportDefinitionSaveCommand command) {
        return Result.ok(reportAppService.updateDefinition(reportNo, command));
    }

    /**
     * 执行报表。
     *
     * @param reportNo 报表编号
     * @param from 日期起
     * @param to 日期止
     * @return 分析结果
     */
    @PostMapping("/{reportNo}/execute")
    @RequirePermission("sys:erp:bi:report:execute")
    public Result<CubeResultView> execute(
            @PathVariable String reportNo,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to) {
        return Result.ok(reportAppService.execute(reportNo, from, to));
    }

    /**
     * 导出报表 CSV（权限码控制）。
     *
     * @param reportNo 报表编号
     * @param from 日期起
     * @param to 日期止
     * @return CSV 文本
     */
    @GetMapping("/{reportNo}/export")
    @RequirePermission("sys:erp:bi:report:export")
    @AuditLog(operation = "导出报表", targetType = "erp_bi_report_def", type = OperateType.EXPORT)
    public Result<String> export(
            @PathVariable String reportNo,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to) {
        return Result.ok(reportAppService.exportCsv(reportNo, from, to));
    }

    /**
     * 报表计划分页。
     *
     * @param query 查询条件
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @return 分页结果
     */
    @GetMapping("/schedule/page")
    @RequirePermission("sys:erp:bi:report:list")
    public Result<PageResult<ReportScheduleView>> pageSchedules(
            @ModelAttribute AnalyticsQuery query,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(reportAppService.pageSchedules(query));
    }

    /**
     * 新增报表计划。
     *
     * @param command 保存命令
     * @return 报表计划
     */
    @PostMapping("/schedule")
    @RequirePermission("sys:erp:bi:report:save")
    @AuditLog(operation = "新增报表计划", targetType = "erp_bi_report_schedule", type = OperateType.CREATE)
    public Result<ReportScheduleView> createSchedule(@RequestBody ReportScheduleSaveCommand command) {
        return Result.ok(reportAppService.createSchedule(command));
    }

    /**
     * 编辑报表计划。
     *
     * @param scheduleNo 计划编号
     * @param command 保存命令
     * @return 报表计划
     */
    @PutMapping("/schedule/{scheduleNo}")
    @RequirePermission("sys:erp:bi:report:save")
    @AuditLog(operation = "编辑报表计划", targetType = "erp_bi_report_schedule", type = OperateType.UPDATE)
    public Result<ReportScheduleView> updateSchedule(@PathVariable String scheduleNo,
                                                     @RequestBody ReportScheduleSaveCommand command) {
        return Result.ok(reportAppService.updateSchedule(scheduleNo, command));
    }

    /**
     * 手动执行一次报表计划。
     *
     * @param scheduleNo 计划编号
     * @return 报表计划
     */
    @PostMapping("/schedule/{scheduleNo}/run")
    @RequirePermission("sys:erp:bi:report:run")
    @AuditLog(operation = "执行报表计划", targetType = "erp_bi_report_schedule", type = OperateType.UPDATE)
    public Result<ReportScheduleView> runSchedule(@PathVariable String scheduleNo) {
        return Result.ok(reportAppService.runSchedule(scheduleNo));
    }
}
