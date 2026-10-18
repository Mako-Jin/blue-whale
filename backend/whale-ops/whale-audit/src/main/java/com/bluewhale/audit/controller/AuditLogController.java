package com.bluewhale.audit.controller;

import com.bluewhale.audit.dto.AuditLogQueryRequest;
import com.bluewhale.audit.service.AuditLogService;
import com.bluewhale.audit.vo.AuditLogVO;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 审计日志查询接口（只读）。
 * <p>服务内路径 {@code /audit/log/**}；经网关路由时按 {@code Path=/audit/**} 规则转发，
 * 外部路径与本文档一致（见 doc 调用示例）。</p>
 * <p>查询本身属敏感操作，已通过 {@code @RequirePermission("sys:audit:log:list")} 限制为审计管理员可见
 * （AUTH-06：由网关权限码头 {@code X-Permission-Codes} + starter {@code RequirePermissionInterceptor} 判定）。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/audit/log")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    /** 分页条件查询：支持用户 / 模块 / 操作类型 / 结果 / 时间范围 / traceId */
    @GetMapping("/page")
    @RequirePermission("sys:audit:log:list")
    public Result<PageResult<AuditLogVO>> page(AuditLogQueryRequest request) {
        return Result.ok(auditLogService.page(request));
    }

    /** 单条详情 */
    @GetMapping("/{id}")
    @RequirePermission("sys:audit:log:list")
    public Result<AuditLogVO> detail(@PathVariable Long id) {
        return Result.ok(auditLogService.getDetail(id));
    }

    /** 按 traceId 聚合一次请求在各服务产生的审计轨迹 */
    @GetMapping("/trace/{traceId}")
    @RequirePermission("sys:audit:log:list")
    public Result<List<AuditLogVO>> listByTraceId(@PathVariable String traceId) {
        return Result.ok(auditLogService.listByTraceId(traceId));
    }
}
