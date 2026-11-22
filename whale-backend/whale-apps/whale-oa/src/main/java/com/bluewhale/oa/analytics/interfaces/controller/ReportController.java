package com.bluewhale.oa.analytics.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.UserContext;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.oa.analytics.application.query.ReportQuery;
import com.bluewhale.oa.analytics.application.service.ReportAppService;
import com.bluewhale.oa.analytics.application.view.ReportViews;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * OA 数据报表接口。
 *
 * <p>查看权限 {@code sys:oa:report:list}、快照权限 {@code sys:oa:report:manage}、
 * 导出权限 {@code sys:oa:report:export}。报表实时只读聚合，快照落库供复盘，
 * 导出 CSV 并登记导出记录。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/oa/v{version}/report", version = "1")
@RequiredArgsConstructor
public class ReportController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "oa_report";

    private final ReportAppService reportAppService;

    /**
     * 报表目录。
     *
     * @return 报表编码、名称、说明与列定义
     */
    @GetMapping("/catalog")
    @RequirePermission("sys:oa:report:list")
    public Result<List<ReportViews.CatalogItem>> catalog() {
        return Result.ok(reportAppService.catalog());
    }

    /**
     * 运行报表。
     *
     * @param code 报表编码
     * @param query 查询条件
     * @return 报表结果
     */
    @GetMapping("/{code}")
    @RequirePermission("sys:oa:report:list")
    public Result<ReportViews.ReportResult> run(@PathVariable String code, ReportQuery query) {
        return Result.ok(reportAppService.run(code, query));
    }

    /**
     * 生成月度报表快照。
     *
     * @param code 报表编码
     * @param period 统计期间（YYYY-MM，缺省本月）
     * @param request 请求上下文
     * @return 快照元信息
     */
    @PostMapping("/{code}/snapshot")
    @RequirePermission("sys:oa:report:manage")
    @AuditLog(operation = "生成报表快照", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<ReportViews.SnapshotView> snapshot(@PathVariable String code,
            @RequestParam(required = false) String period, HttpServletRequest request) {
        return Result.ok(reportAppService.createSnapshot(code, period,
                UserContext.requireUserId(request), WebHeaders.username(request)));
    }

    /**
     * 快照分页。
     *
     * @param code 报表编码（可为空）
     * @param current 页码
     * @param size 每页条数
     * @return 快照分页
     */
    @GetMapping("/snapshots/page")
    @RequirePermission("sys:oa:report:list")
    public Result<PageResult<ReportViews.SnapshotView>> snapshots(
            @RequestParam(required = false) String code,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(reportAppService.pageSnapshots(code, current, size));
    }

    /**
     * 读取快照内容。
     *
     * @param id 快照ID
     * @return 快照对应的报表结果
     */
    @GetMapping("/snapshots/{id}")
    @RequirePermission("sys:oa:report:list")
    public Result<ReportViews.ReportResult> snapshotDetail(@PathVariable Long id) {
        return Result.ok(reportAppService.snapshotDetail(id));
    }

    /**
     * 导出报表 CSV。
     *
     * @param code 报表编码
     * @param query 查询条件
     * @param request 请求上下文
     * @return CSV 文件
     */
    @GetMapping("/{code}/export")
    @RequirePermission("sys:oa:report:export")
    @AuditLog(operation = "导出数据报表", type = OperateType.EXPORT, targetType = TARGET_TYPE)
    public ResponseEntity<byte[]> export(@PathVariable String code, ReportQuery query,
            HttpServletRequest request) {
        ReportViews.ExportFile file = reportAppService.export(code, query,
                UserContext.requireUserId(request), WebHeaders.username(request));
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFilename() + "\"")
                .body(file.getContent());
    }
}
