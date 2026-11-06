package com.bluewhale.party.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.party.analytics.application.service.AnalyticsAppService;
import com.bluewhale.party.analytics.application.view.MetricItemView;
import com.bluewhale.party.analytics.application.view.MetricOverviewView;
import com.bluewhale.party.common.PartyPermissions;
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
 * 党务数据看板接口（M-P4）。
 *
 * <p>查看、刷新、导出拆成三个权限：刷新会覆盖当日快照（可追溯性受影响），
 * 导出会把聚合数带出系统，两者都比「看一眼」更需要单独控权。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/party/metric")
@RequiredArgsConstructor
public class PartyMetricController {

    /** 导出文件名时间戳格式 */
    private static final DateTimeFormatter EXPORT_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** CSV 媒体类型 */
    private static final String CSV_TYPE = "text/csv;charset=UTF-8";

    private final AnalyticsAppService analyticsAppService;

    /**
     * 党务看板总览（无当日快照时实时计算，并标记 live）。
     *
     * @param statDate 统计日
     * @param branchId 支部 ID
     * @param request 当前请求
     * @return 总览
     */
    @GetMapping("/overview")
    @RequirePermission(PartyPermissions.METRIC_LIST)
    public Result<MetricOverviewView> overview(
            @RequestParam(value = "statDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate statDate,
            @RequestParam(value = "branchId", required = false) Long branchId,
            HttpServletRequest request) {
        return Result.ok(analyticsAppService.partyOverview(statDate, branchId,
                PartyActorContext.from(request)));
    }

    /**
     * 刷新党务快照（全租户 + 每个在册支部），覆盖写当日数据。
     *
     * @param statDate 统计日
     * @param request 当前请求
     * @return 全租户指标
     */
    @PostMapping("/refresh")
    @RequirePermission(PartyPermissions.METRIC_MANAGE)
    @AuditLog(operation = "刷新党务统计快照", type = OperateType.UPDATE, targetType = "pty_metric_snapshot")
    public Result<List<MetricItemView>> refresh(
            @RequestParam(value = "statDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate statDate,
            HttpServletRequest request) {
        return Result.ok(analyticsAppService.refreshParty(statDate, PartyActorContext.from(request)));
    }

    /**
     * 党务数据脱敏导出（CSV，只含聚合数）。
     *
     * @param from 起始统计日
     * @param to 结束统计日
     * @param branchId 支部 ID
     * @param request 当前请求
     * @return CSV 附件
     */
    @GetMapping("/export")
    @RequirePermission(PartyPermissions.METRIC_EXPORT)
    public ResponseEntity<byte[]> export(
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "branchId", required = false) Long branchId,
            HttpServletRequest request) {
        String csv = analyticsAppService.exportPartyCsv(from, to, branchId,
                PartyActorContext.from(request));
        String filename = "party-metric-" + EXPORT_TIME.format(LocalDateTime.now()) + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(filename, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(CSV_TYPE))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
