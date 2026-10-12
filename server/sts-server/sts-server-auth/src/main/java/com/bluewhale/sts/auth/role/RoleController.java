package com.bluewhale.sts.auth.role;

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

import java.util.List;

/**
 * 角色接口 —— 角色的增删改查，以及**把资源授给角色 / 撤销授权**。
 *
 * <p><b>授权挂在角色下（{@code /roles/{id}/resources}），不单开
 * {@code /role-resources} 一类的平级资源。</b>理由是授权关系是角色的**内部边**：
 * 它没有脱离角色与资源独立存在的意义，也没有独立的生命周期（不存在"无角色的授权"）。
 * 挂在角色下，URL 本身就说明了"改的是这个角色的授权集合"，
 * 同时天然带上了归属校验 —— {@code {id}} 是必经的一环。
 *
 * <p><b>撤销的路径用 {@code resourceId} 而不是授权行的 {@code grantId}。</b>
 * 调用方（授权界面）手里是两个东西：资源树（给主键）和已授列表（给主键 + 授权主键）。
 * 用资源主键可以让"授"与"撤"完全对称 —— 同一个 (角色, 资源) 二元组就是这条边的身份，
 * 而 {@code grantId} 会随 upsert 换行、随复活换 id，把它写进路径会让调用方
 * 必须记住一行状态。返回体里仍然给出 {@code grantId}，供需要精确定位的场合使用。
 *
 * <p>三个写角色的方法与两个改授权的方法都标了 {@link Audited}，读方法没标：
 * 查询不产生审计（理由见 {@code UserController} 的类注释）。
 * <b>授权与撤销是两个独立动作</b>，虽然共用 {@code auth:role:update} 权限码 ——
 * 见 {@code AuthAuditActions.ROLE_GRANT} 的注释。
 */
@RestController
@RequestMapping("/api/auth/roles")
@Tag(name = "角色", description = "角色的增删改查与角色资源授权")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @RequiresPermission(AuthPermissions.ROLE_READ)
    @Operation(summary = "分页查询角色")
    public R<PageResult<RoleVO>> page(@ParameterObject RoleQuery query) {
        return R.ok(roleService.page(query));
    }

    @GetMapping("/{id}")
    @RequiresPermission(AuthPermissions.ROLE_READ)
    @Operation(summary = "查询角色详情")
    public R<RoleVO> detail(@PathVariable @Parameter(description = "角色主键") Long id) {
        return R.ok(roleService.detail(id));
    }

    @PostMapping
    @RequiresPermission(AuthPermissions.ROLE_CREATE)
    @Audited(action = AuthAuditActions.ROLE_CREATE, resource = AuthResources.ROLE)
    @Operation(summary = "新增角色", description = "编码曾被已删除的角色占用时会复活那一行")
    public R<RoleVO> create(@Valid @RequestBody RoleSaveRequest request) {
        RoleVO created = roleService.create(request);
        // 新建时主键是自增出来的，调用前并不存在，因此只能在业务跑完之后补
        AuditContext.target(created.getId());
        return R.ok(created);
    }

    @PutMapping("/{id}")
    @RequiresPermission(AuthPermissions.ROLE_UPDATE)
    @Audited(action = AuthAuditActions.ROLE_UPDATE, resource = AuthResources.ROLE)
    @Operation(summary = "修改角色", description = "角色编码与所属租户不可修改；系统内置角色不可停用")
    public R<RoleVO> update(@PathVariable @Parameter(description = "角色主键") Long id,
                            @Valid @RequestBody RoleSaveRequest request) {
        RoleVO updated = roleService.update(id, request);
        AuditContext.target(id);
        return R.ok(updated);
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(AuthPermissions.ROLE_DELETE)
    @Audited(action = AuthAuditActions.ROLE_DELETE, resource = AuthResources.ROLE)
    @Operation(summary = "删除角色",
            description = "系统内置角色、有子角色、有成员时拒绝删除；角色自身的授权会被一并撤销")
    public R<Void> delete(@PathVariable @Parameter(description = "角色主键") Long id) {
        roleService.delete(id);
        AuditContext.target(id);
        return R.ok();
    }

    @GetMapping("/{id}/resources")
    @RequiresPermission(AuthPermissions.ROLE_READ)
    @Operation(summary = "查询角色已授予的资源")
    public R<List<RoleResourceVO>> listGrants(@PathVariable @Parameter(description = "角色主键") Long id) {
        return R.ok(roleService.listGrants(id));
    }

    @PostMapping("/{id}/resources")
    @RequiresPermission(AuthPermissions.ROLE_UPDATE)
    @Audited(action = AuthAuditActions.ROLE_GRANT, resource = AuthResources.ROLE)
    @Operation(summary = "给角色授予资源",
            description = "同一个角色与资源只保留一条有效授权；重复调用会改效果而不是堆行")
    public R<RoleResourceVO> grant(@PathVariable @Parameter(description = "角色主键") Long id,
                                   @Valid @RequestBody RoleResourceGrantRequest request) {
        RoleResourceVO granted = roleService.grant(id, request);
        AuditContext.target(id);
        return R.ok(granted);
    }

    @DeleteMapping("/{id}/resources/{resourceId}")
    @RequiresPermission(AuthPermissions.ROLE_UPDATE)
    @Audited(action = AuthAuditActions.ROLE_REVOKE, resource = AuthResources.ROLE)
    @Operation(summary = "撤销角色的资源授权", description = "从未授予过的资源返回 40407，不与撤销成功混淆")
    public R<Void> revoke(@PathVariable @Parameter(description = "角色主键") Long id,
                          @PathVariable @Parameter(description = "资源主键") Long resourceId) {
        roleService.revoke(id, resourceId);
        AuditContext.target(id);
        return R.ok();
    }

}
