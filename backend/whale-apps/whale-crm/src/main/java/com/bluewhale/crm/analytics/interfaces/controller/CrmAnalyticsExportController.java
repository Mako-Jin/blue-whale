package com.bluewhale.crm.analytics.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.crm.analytics.application.command.AnalyticsExportCreateCommand;
import com.bluewhale.crm.analytics.application.service.AnalyticsExportAppService;
import com.bluewhale.crm.analytics.domain.model.AnalyticsExport;
import com.bluewhale.crm.analytics.domain.model.ExportType;
import com.bluewhale.crm.common.CrmPermissions;
import com.bluewhale.crm.interfaces.support.CrmActorContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CRM 分析导出接口。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/crm/analytics/export")
@RequiredArgsConstructor
public class CrmAnalyticsExportController {

    private final AnalyticsExportAppService analyticsExportAppService;

    /**
     * 创建分析导出。
     *
     * @param command 创建命令
     * @param request 当前请求
     * @return 导出任务
     */
    @PostMapping
    @RequirePermission(CrmPermissions.ANALYTICS_EXPORT)
    @AuditLog(operation = "创建分析导出", type = OperateType.EXPORT, targetType = "crm_analytics_export")
    public Result<AnalyticsExport> create(@Valid @RequestBody AnalyticsExportCreateCommand command,
                                          HttpServletRequest request) {
        return Result.ok(analyticsExportAppService.create(ExportType.from(command.getExportType()),
                command.getFiltersJson(), CrmActorContext.from(request)));
    }

    /**
     * 查询导出内容。
     *
     * @param id 导出任务 ID
     * @param request 当前请求
     * @return 导出任务（含 CSV）
     */
    @GetMapping("/{id}/content")
    @RequirePermission(CrmPermissions.ANALYTICS_EXPORT)
    public Result<AnalyticsExport> content(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(analyticsExportAppService.content(id, CrmActorContext.from(request)));
    }
}
