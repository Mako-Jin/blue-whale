package com.bluewhale.audit.controller;

import com.bluewhale.audit.dto.AuditLogQueryRequest;
import com.bluewhale.audit.service.AuditLogService;
import com.bluewhale.audit.vo.AuditLogVO;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
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
 * <p>读审计亦须留痕（AUTH-07）：本类 3 个查询方法均标注 {@code @AuditLog(type=QUERY)}，
 * 调用将产生 QUERY 元审计记录并落到同库 {@code audit_log}，可经 {@code /audit/log/page} 反查。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/audit/log")
@RequiredArgsConstructor
public class AuditLogController {

    /** 元审计的操作对象类型（3 个查询方法共用，避免魔法值重复） */
    private static final String TARGET_TYPE = "audit_log";

    private final AuditLogService auditLogService;

    /** 分页条件查询：支持用户 / 模块 / 操作类型 / 结果 / 时间范围 / traceId；本方法调用将产生 QUERY 元审计 */
    @GetMapping("/page")
    @RequirePermission("sys:audit:log:list")
    @AuditLog(operation = "分页查询审计日志", type = OperateType.QUERY,
            targetType = TARGET_TYPE, recordResult = false)
    public Result<PageResult<AuditLogVO>> page(AuditLogQueryRequest request) {
        return Result.ok(auditLogService.page(request));
    }

    /** 单条详情；本方法调用将产生 QUERY 元审计 */
    @GetMapping("/{id}")
    @RequirePermission("sys:audit:log:list")
    @AuditLog(operation = "查看审计日志详情", type = OperateType.QUERY,
            targetType = TARGET_TYPE, recordResult = false)
    public Result<AuditLogVO> detail(@PathVariable Long id) {
        return Result.ok(auditLogService.getDetail(id));
    }

    /** 按 traceId 聚合一次请求在各服务产生的审计轨迹；本方法调用将产生 QUERY 元审计 */
    @GetMapping("/trace/{traceId}")
    @RequirePermission("sys:audit:log:list")
    @AuditLog(operation = "按链路查询审计日志", type = OperateType.QUERY,
            targetType = TARGET_TYPE, recordResult = false)
    public Result<List<AuditLogVO>> listByTraceId(@PathVariable String traceId) {
        return Result.ok(auditLogService.listByTraceId(traceId));
    }
}
