package com.bluewhale.sts.auth.tenant;

import com.bluewhale.sts.auth.authz.RequiresPermission;
import com.bluewhale.sts.auth.common.AuthPermissions;
import com.bluewhale.sts.common.basic.model.PageResult;
import com.bluewhale.sts.common.basic.model.R;
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
    @Operation(summary = "新增租户")
    public R<TenantVO> create(@Valid @RequestBody TenantSaveRequest request) {
        return R.ok(tenantService.create(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission(AuthPermissions.TENANT_UPDATE)
    @Operation(summary = "修改租户", description = "租户编码不可修改")
    public R<TenantVO> update(@PathVariable @Parameter(description = "租户主键") Long id,
                              @Valid @RequestBody TenantSaveRequest request) {
        return R.ok(tenantService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(AuthPermissions.TENANT_DELETE)
    @Operation(summary = "删除租户", description = "存在子租户时拒绝删除")
    public R<Void> delete(@PathVariable @Parameter(description = "租户主键") Long id) {
        tenantService.delete(id);
        return R.ok();
    }

}
