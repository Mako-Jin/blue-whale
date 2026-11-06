package com.bluewhale.lms.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.lms.analytics.application.service.AnalyticsAppService;
import com.bluewhale.lms.analytics.application.view.AnalyticsOverviewView;
import com.bluewhale.lms.analytics.application.view.MetricSnapshotView;
import com.bluewhale.lms.analytics.application.view.RefreshResultView;
import com.bluewhale.lms.analytics.domain.model.MetricQuery;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.common.LmsPermissions;
import com.bluewhale.lms.interfaces.support.LmsActorContext;
import com.bluewhale.lms.interfaces.support.PageParams;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * 学习看板接口（M6）。
 *
 * <p>四个端点分工：{@code /overview} 只读（缺快照时实时算但不落库）、
 * {@code /refresh} 才写库、{@code /snapshot/page} 看历史、
 * {@code /export} 导出聚合数。拆成「读」与「刷新」两个动作，是为了让
 * 「每天一份快照」的历史曲线不被访问频率污染。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/lms/analytics")
@RequiredArgsConstructor
public class LmsAnalyticsController {

    /** CSV 导出文件名 */
    private static final String EXPORT_FILE = "lms-analytics.csv";

    private final AnalyticsAppService analyticsAppService;

    /**
     * 看板总览。
     *
     * @param statDate 统计日（为空取今天）
     * @param request 当前请求
     * @return 总览
     */
    @GetMapping("/overview")
    @RequirePermission(LmsPermissions.ANALYTICS_VIEW)
    public Result<AnalyticsOverviewView> overview(
            @RequestParam(value = "statDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate statDate,
            HttpServletRequest request) {
        return Result.ok(analyticsAppService.overview(statDate, actor(request)));
    }

    /**
     * 刷新并落库某统计日的全院快照（同日重跑覆盖）。
     *
     * @param statDate 统计日（为空取今天）
     * @param request 当前请求
     * @return 刷新结果
     */
    @PostMapping("/refresh")
    @RequirePermission(LmsPermissions.ANALYTICS_REFRESH)
    @AuditLog(operation = "刷新学习看板快照", type = OperateType.CREATE, targetType = "lr_metric_snapshot")
    public Result<RefreshResultView> refresh(
            @RequestParam(value = "statDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate statDate,
            HttpServletRequest request) {
        return Result.ok(analyticsAppService.refresh(statDate, actor(request)));
    }

    /**
     * 历史快照分页。
     *
     * @param from 起始统计日
     * @param to 结束统计日
     * @param scopeType 统计范围
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/snapshot/page")
    @RequirePermission(LmsPermissions.ANALYTICS_VIEW)
    public Result<PageResult<MetricSnapshotView>> page(
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "scopeType", required = false) String scopeType,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        MetricQuery query = new MetricQuery();
        query.setFrom(from);
        query.setTo(to);
        query.setScopeType(scopeType);
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(analyticsAppService.page(query, actor(request)));
    }

    /**
     * 导出近 N 天快照为 CSV（聚合口径，不含个人信息）。
     *
     * @param days 天数（1–366）
     * @param request 当前请求
     * @return CSV 附件
     */
    @GetMapping("/export")
    @RequirePermission(LmsPermissions.ANALYTICS_EXPORT)
    @AuditLog(operation = "导出学习看板数据", type = OperateType.EXPORT, targetType = "lr_metric_snapshot")
    public ResponseEntity<byte[]> export(
            @RequestParam(value = "days", required = false, defaultValue = "30") int days,
            HttpServletRequest request) {
        String csv = analyticsAppService.exportCsv(days, actor(request));
        byte[] body = csv.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + EXPORT_FILE + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    /**
     * 解析操作人。
     *
     * @param request 当前请求
     * @return 操作人
     */
    private LmsActor actor(HttpServletRequest request) {
        return LmsActorContext.from(request);
    }
}
