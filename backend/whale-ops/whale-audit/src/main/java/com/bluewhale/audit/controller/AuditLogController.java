package com.bluewhale.audit.controller;

import com.bluewhale.audit.dto.AuditLogQueryRequest;
import com.bluewhale.audit.service.AuditLogService;
import com.bluewhale.audit.vo.AuditLogVO;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.entity.AuditLogRecord;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 审计日志查询接口。
 * <p>服务内路径 {@code /audit/log/**}；经网关路由时按 {@code Path=/audit/**} 规则转发，
 * 外部路径与本文档一致（见 doc 调用示例）。</p>
 * <p>查询本身属敏感操作，已通过 {@code @RequirePermission("sys:audit:log:list")} 限制为审计管理员可见
 * （AUTH-06：由网关权限码头 {@code X-Permission-Codes} + starter {@code RequirePermissionInterceptor} 判定）。</p>
 * <p>读审计亦须留痕（AUTH-07）：本类 3 个查询方法均标注 {@code @AuditLog(type=QUERY)}，
 * 调用将产生 QUERY 元审计记录并落到同库 {@code audit_log}，可经 {@code /audit/log/page} 反查。</p>
 * <p>例外：{@code POST /audit/log/ingest} 为服务间内网摄入入口（P1-3 跨库汇聚），
 * 不校验用户权限、不产生元审计，由网关 {@code internal-deny} 屏蔽外部访问。</p>
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

    /**
     * 按条件导出审计日志为 CSV 并上传归档（AUTH-08）。
     *
     * <p>入参复用分页查询 DTO {@link AuditLogQueryRequest}（时间范围 / 用户 / 模块 / 操作类型 / 结果 / traceId）；
     * 行数上限由 {@code whale.audit.export.max-rows} 控制，超限仅取上限行并告警。</p>
     *
     * <p>CSV 生成后经 whale-file 内网接口 {@code POST /file/internal/upload}
     * （{@code bucket=bluewhale-audit-archive}）上传；返回 {@code data} 字段与 whale-file 的
     * {@code FileUploadVO} 对齐（{@code id/fileName/originalName/bucket/objectKey/fileSize/contentType/md5}），
     * 本模块不重复定义该类型，以 {@code Map} 承载。</p>
     *
     * <p>本方法调用将产生 EXPORT 元审计；上传失败时由服务返回受控失败（{@code code != 0}），不外抛原始异常。
     * 导出只读 {@code audit_log}，不做任何删除/更新。</p>
     *
     * @param request 审计日志查询条件（复用分页查询 DTO）
     * @return 上传结果响应；无匹配记录时 {@code data} 为空 Map
     */
    @PostMapping("/export")
    @RequirePermission("sys:audit:log:list")
    @AuditLog(operation = "导出审计日志", type = OperateType.EXPORT,
            targetType = TARGET_TYPE, recordResult = false)
    public Result<Map<String, Object>> export(@RequestBody AuditLogQueryRequest request) {
        return auditLogService.exportCsv(request);
    }

    /**
     * 接收其他服务转发的审计记录（内网摄入，P1-3 跨库汇聚）。
     *
     * <p>调用方为平台内服务（当前仅 whale-auth 试点），不携带用户身份与权限码头，
     * 因此<b>不</b>加 {@code @RequirePermission}；同时不标注 {@code @AuditLog}，
     * 避免摄入动作产生新的审计记录形成放大。外部访问由网关 {@code internal-deny} 返回 403。</p>
     *
     * @param records 审计记录列表（单次最多 50 条，主键由本库重新生成）
     * @return 实际入库条数
     */
    @PostMapping("/ingest")
    public Result<Integer> ingest(@RequestBody List<AuditLogRecord> records) {
        return Result.ok(auditLogService.ingest(records));
    }
}
