package com.bluewhale.auth.controller;

import com.bluewhale.auth.dto.AssignPermissionsRequest;
import com.bluewhale.auth.dto.RoleCreateRequest;
import com.bluewhale.auth.dto.RoleUpdateRequest;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.auth.service.SysRoleService;
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
 * @author BlueWhale
 */
@RestController
@RequestMapping("/role")
@RequiredArgsConstructor
public class RoleController {

    private final SysRoleService roleService;

    @GetMapping("/page")
    @RequirePermission("sys:role:list")
    public Result<PageResult<RoleVO>> page(PageQuery pageQuery,
                                           @RequestParam(required = false) String keyword) {
        return Result.ok(roleService.page(pageQuery.getCurrent(), pageQuery.getSize(), keyword));
    }

    @GetMapping("/{id}")
    @RequirePermission("sys:role:list")
    public Result<RoleVO> detail(@PathVariable Long id) {
        return Result.ok(roleService.getDetail(id));
    }

    @PostMapping
    @RequirePermission("sys:role:add")
    @AuditLog(operation = "创建角色", type = OperateType.CREATE, targetType = "sys_role")
    public Result<RoleVO> create(@Valid @RequestBody RoleCreateRequest request) {
        return Result.ok(roleService.create(request));
    }

    @PutMapping("/{id}")
    @RequirePermission("sys:role:edit")
    @AuditLog(operation = "更新角色", type = OperateType.UPDATE, targetType = "sys_role", targetId = "#id")
    public Result<RoleVO> update(@PathVariable Long id, @Valid @RequestBody RoleUpdateRequest request) {
        return Result.ok(roleService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("sys:role:delete")
    @AuditLog(operation = "删除角色", type = OperateType.DELETE, targetType = "sys_role", targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return Result.ok();
    }

    /** 全量覆盖分配权限：授权变更属敏感操作，必留痕 */
    @PostMapping("/{id}/permissions")
    @RequirePermission("sys:role:assign")
    @AuditLog(operation = "分配角色权限", type = OperateType.GRANT, targetType = "sys_role", targetId = "#id")
    public Result<Void> assignPermissions(@PathVariable Long id,
                                          @Valid @RequestBody AssignPermissionsRequest request) {
        roleService.assignPermissions(id, request.getPermissionIds());
        return Result.ok();
    }

    /** 查询角色已分配权限 ID */
    @GetMapping("/{id}/permissions")
    @RequirePermission("sys:role:list")
    public Result<List<Long>> permissionIds(@PathVariable Long id) {
        return Result.ok(roleService.listPermissionIds(id));
    }
}
