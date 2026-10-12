package com.bluewhale.sts.auth.tenant;

import com.bluewhale.sts.auth.authz.RequiresPermission;
import com.bluewhale.sts.auth.common.AuthAuditActions;
import com.bluewhale.sts.auth.common.AuthPermissions;
import com.bluewhale.sts.auth.common.AuthResources;
import com.bluewhale.sts.common.basic.model.PageResult;
import com.bluewhale.sts.common.basic.model.R;
import com.bluewhale.sts.components.audit.AuditContext;
import com.bluewhale.sts.components.audit.Audited;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 租户接口。
 *
 * <p>每个方法都标了 {@link RequiresPermission}：这是本切片引入的**运行时鉴权**
 * 在真实接口上的第一次使用。未认证返回 401，认证但权限不足返回 403。
 *
 * <p>三个写方法标了 {@link Audited}，两个读方法没标 —— 理由见 {@code UserController}
 * 的类注释（查询不产生审计）。
 *
 * <p><b>租户的审计尤其值得留意"被拒绝"的那些记录：</b>删除一个还有子租户的租户会被
 * 业务规则拒绝（"存在子租户时拒绝删除"），这属于"有人试图做一件不该做的事"，
 * 而它同样会以 HTTP 200 + 业务码的形式返回。审计拦截器靠 {@code ResponseOutcome}
 * 拿到这个码，把它如实记成 FAILURE —— 详见 {@code AuditInterceptor}。
 */
@RestController
@RequestMapping("/api/auth/tenants")
@Tag(name = "租户", description = "租户的增删改查")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping
    @RequiresPermission(AuthPermissions.TENANT_READ)
    @Operation(summary = "分页查询租户")
    public R<PageResult<TenantVO>> page(@ParameterObject TenantQuery query) {
        return R.ok(tenantService.page(query));
    }

    @GetMapping("/{id}")
    @RequiresPermission(AuthPermissions.TENANT_READ)
    @Operation(summary = "查询租户详情")
    public R<TenantVO> detail(@PathVariable @Parameter(description = "租户主键") Long id) {
        return R.ok(tenantService.detail(id));
    }

    @PostMapping
    @RequiresPermission(AuthPermissions.TENANT_CREATE)
    @Audited(action = AuthAuditActions.TENANT_CREATE, resource = AuthResources.TENANT)
    @Operation(summary = "新增租户")
    public R<TenantVO> create(@Valid @RequestBody TenantSaveRequest request) {
        TenantVO created = tenantService.create(request);
        // 新建时主键是自增出来的，调用前并不存在，因此只能在业务跑完之后补
        AuditContext.target(created.getId());
        return R.ok(created);
    }

    @PutMapping("/{id}")
    @RequiresPermission(AuthPermissions.TENANT_UPDATE)
    @Audited(action = AuthAuditActions.TENANT_UPDATE, resource = AuthResources.TENANT)
    @Operation(summary = "修改租户", description = "租户编码不可修改")
    public R<TenantVO> update(@PathVariable @Parameter(description = "租户主键") Long id,
                              @Valid @RequestBody TenantSaveRequest request) {
        TenantVO updated = tenantService.update(id, request);
        AuditContext.target(id);
        return R.ok(updated);
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(AuthPermissions.TENANT_DELETE)
    @Audited(action = AuthAuditActions.TENANT_DELETE, resource = AuthResources.TENANT)
    @Operation(summary = "删除租户", description = "存在子租户时拒绝删除")
    public R<Void> delete(@PathVariable @Parameter(description = "租户主键") Long id) {
        tenantService.delete(id);
        AuditContext.target(id);
        return R.ok();
    }

}
