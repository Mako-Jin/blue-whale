package com.bluewhale.auth.controller;

import com.bluewhale.auth.application.role.RoleAppService;
import com.bluewhale.auth.application.role.command.AssignRolePermissionsCommand;
import com.bluewhale.auth.application.role.command.CreateRoleCommand;
import com.bluewhale.auth.application.role.command.UpdateRoleCommand;
import com.bluewhale.auth.application.role.query.RolePageQuery;
import com.bluewhale.auth.dto.AssignPermissionsRequest;
import com.bluewhale.auth.dto.RoleCreateRequest;
import com.bluewhale.auth.dto.RoleUpdateRequest;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.auth.vo.RoleVO;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.query.PageQuery;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 角色管理：CRUD / 分配权限（权限点 + 菜单）
 *
 * <p>统一委派 {@link RoleAppService}；REST 路径与出入参保持不变（AUTH-17 收口）。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/role")
@RequiredArgsConstructor
public class RoleController {

    private final RoleAppService roleAppService;

    /**
     * 分页查询角色
     *
     * @param pageQuery 分页参数（当前页与每页条数）
     * @param keyword 角色编码或名称模糊匹配关键字（可空）
     * @return 角色分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:role:list")
    public Result<PageResult<RoleVO>> page(PageQuery pageQuery,
                                           @RequestParam(required = false) String keyword) {
        return Result.ok(roleAppService.page(
                new RolePageQuery(pageQuery.getCurrent(), pageQuery.getSize(), keyword)));
    }

    /**
     * 角色详情
     *
     * @param id 角色 ID
     * @return 角色视图对象
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:role:list")
    public Result<RoleVO> detail(@PathVariable Long id) {
        return Result.ok(roleAppService.getDetail(id));
    }

    /**
     * 创建角色
     *
     * @param request 角色创建请求参数
     * @return 新建角色视图对象
     */
    @PostMapping
    @RequirePermission("sys:role:add")
    @AuditLog(operation = "创建角色", type = OperateType.CREATE, targetType = "sys_role")
    public Result<RoleVO> create(@Valid @RequestBody RoleCreateRequest request) {
        return Result.ok(roleAppService.create(new CreateRoleCommand(request.getRoleCode(),
                request.getRoleName(), request.getDescription(), request.getSortNo(), request.getStatus())));
    }

    /**
     * 更新角色
     *
     * @param id 角色 ID
     * @param request 角色更新请求参数
     * @return 更新后的角色视图对象
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:role:edit")
    @AuditLog(operation = "更新角色", type = OperateType.UPDATE, targetType = "sys_role", targetId = "#id")
    public Result<RoleVO> update(@PathVariable Long id, @Valid @RequestBody RoleUpdateRequest request) {
        return Result.ok(roleAppService.update(id, new UpdateRoleCommand(request.getRoleName(),
                request.getDescription(), request.getSortNo(), request.getStatus())));
    }

    /**
     * 删除角色
     *
     * @param id 角色 ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:role:delete")
    @AuditLog(operation = "删除角色", type = OperateType.DELETE, targetType = "sys_role", targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        roleAppService.delete(id);
        return Result.ok();
    }

    /**
     * 全量覆盖分配权限：授权变更属敏感操作，必留痕
     *
     * @param id 角色 ID
     * @param request 分配权限请求（含权限点 ID 列表）
     * @return 操作结果
     */
    @PostMapping("/{id}/permissions")
    @RequirePermission("sys:role:assign")
    @AuditLog(operation = "分配角色权限", type = OperateType.GRANT, targetType = "sys_role", targetId = "#id")
    public Result<Void> assignPermissions(@PathVariable Long id,
                                          @Valid @RequestBody AssignPermissionsRequest request) {
        roleAppService.assignPermissions(id,
                new AssignRolePermissionsCommand(request.getPermissionIds()));
        return Result.ok();
    }

    /**
     * 查询角色已分配权限 ID
     *
     * @param id 角色 ID
     * @return 角色已分配的权限点 ID 列表
     */
    @GetMapping("/{id}/permissions")
    @RequirePermission("sys:role:list")
    public Result<List<Long>> permissionIds(@PathVariable Long id) {
        return Result.ok(roleAppService.listPermissionIds(id));
    }
}
