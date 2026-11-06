package com.bluewhale.party.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.party.analytics.application.service.AnalyticsAppService;
import com.bluewhale.party.analytics.application.view.MetricItemView;
import com.bluewhale.party.analytics.application.view.MetricOverviewView;
import com.bluewhale.party.common.PartyPermissions;
import com.bluewhale.party.integration.application.service.NotifyAppService;
import com.bluewhale.party.integration.application.view.NoticeScanResultView;
import com.bluewhale.party.interfaces.support.PartyActorContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 工会数据看板接口（M-P4）。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/party/union")
@RequiredArgsConstructor
public class PartyUnionMetricController {

    /** 导出文件名时间戳格式 */
    private static final DateTimeFormatter EXPORT_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** CSV 媒体类型 */
    private static final String CSV_TYPE = "text/csv;charset=UTF-8";

    private final AnalyticsAppService analyticsAppService;

    private final NotifyAppService notifyAppService;

    /**
     * 工会看板总览。
     *
     * @param statDate 统计日
     * @param request 当前请求
     * @return 总览
     */
    @GetMapping("/metric/overview")
    @RequirePermission(PartyPermissions.UNION_METRIC_LIST)
    public Result<MetricOverviewView> overview(
            @RequestParam(value = "statDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate statDate,
            HttpServletRequest request) {
        return Result.ok(analyticsAppService.unionOverview(statDate, PartyActorContext.from(request)));
    }

    /**
     * 刷新工会快照。
     *
     * @param statDate 统计日
     * @param request 当前请求
     * @return 指标
     */
    @PostMapping("/metric/refresh")
    @RequirePermission(PartyPermissions.UNION_METRIC_MANAGE)
    @AuditLog(operation = "刷新工会统计快照", type = OperateType.UPDATE, targetType = "pty_metric_snapshot")
    public Result<List<MetricItemView>> refresh(
            @RequestParam(value = "statDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate statDate,
            HttpServletRequest request) {
        return Result.ok(analyticsAppService.refreshUnion(statDate, PartyActorContext.from(request)));
    }

    /**
     * 工会数据脱敏导出。
     *
     * @param from 起始统计日
     * @param to 结束统计日
     * @param request 当前请求
     * @return CSV 附件
     */
    @GetMapping("/metric/export")
    @RequirePermission(PartyPermissions.UNION_METRIC_LIST)
    public ResponseEntity<byte[]> export(
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            HttpServletRequest request) {
        String csv = analyticsAppService.exportUnionCsv(from, to, PartyActorContext.from(request));
        String filename = "union-metric-" + EXPORT_TIME.format(LocalDateTime.now()) + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(filename, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(CSV_TYPE))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 工会活动通知扫描（每场活动每个会员只通知一次）。
     *
     * @return 扫描结果
     */
    @PostMapping("/notice/scan")
    @RequirePermission(PartyPermissions.UNION_METRIC_MANAGE)
    @AuditLog(operation = "扫描并发送工会活动通知", type = OperateType.CREATE, targetType = "pty_notify_log")
    public Result<NoticeScanResultView> scanNotice() {
        return Result.ok(notifyAppService.scanUnion());
    }
}
