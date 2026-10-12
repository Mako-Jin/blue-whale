package com.bluewhale.sts.audit.auditlog;

import com.bluewhale.sts.auth.authz.RequiresPermission;
import com.bluewhale.sts.auth.common.AuthPermissions;
import com.bluewhale.sts.common.basic.model.PageResult;
import com.bluewhale.sts.common.basic.model.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审计查询接口。
 *
 * <p><b>整个类只有 GET，没有 POST / PUT / DELETE —— 这不是"本期先不做"。</b>
 * 审计记录由拦截器在真实请求结束时自动产生，前端上报只作"业务意图"补充
 * （那属于日志与事件两条管道）。本域**刻意不提供任何写入接口**：
 * 只要存在一个"接受写入审计"的入口，审计表就从"服务端自己的账本"
 * 降级成"谁都能往里写的一张表"，而它的全部价值正建立在不是这样。
 *
 * <p>因此这里的"不可篡改"不靠权限保护，靠**接口不存在**。权限只负责
 * "谁有资格看"（{@link AuthPermissions#AUDIT_READ}），不负责"谁有资格改" ——
 * 后者没有可授予的对象。
 */
@RestController
@RequestMapping("/api/audit/logs")
@Tag(name = "审计日志", description = "由后端拦截器自动产生的操作留痕，只读、不可改、不可删")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    @RequiresPermission(AuthPermissions.AUDIT_READ)
    @Operation(summary = "分页查询审计记录",
            description = "按服务端记录时间倒序。时间窗缺省为最近 7 天。可按动作、资源、对象、操作人、结果过滤。")
    public R<PageResult<AuditLogVO>> page(@ParameterObject AuditLogQuery query) {
        return R.ok(auditLogService.page(query));
    }

    @GetMapping("/stats")
    @RequiresPermission(AuthPermissions.AUDIT_READ)
    @Operation(summary = "按动作聚合统计",
            description = "返回时间窗内的总条数、失败条数与各动作分布（含各自的失败条数），只包含出现过的动作。")
    public R<AuditStatsVO> stats(@ParameterObject AuditStatsQuery query) {
        return R.ok(auditLogService.stats(query));
    }

    @GetMapping("/{id}")
    @RequiresPermission(AuthPermissions.AUDIT_READ)
    @Operation(summary = "查询审计记录详情")
    public R<AuditLogVO> detail(@PathVariable @Parameter(description = "审计记录主键") long id) {
        return R.ok(auditLogService.detail(id));
    }

}
