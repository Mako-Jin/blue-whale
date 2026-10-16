package com.bluewhale.auth.controller;

import com.bluewhale.auth.dto.AssignPermissionsRequest;
import com.bluewhale.auth.dto.RoleCreateRequest;
import com.bluewhale.auth.dto.RoleUpdateRequest;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.auth.service.SysRoleService;
import com.bluewhale.auth.vo.RoleVO;
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
    public Result<PageResult<RoleVO>> page(PageQuery pageQuery,
                                           @RequestParam(required = false) String keyword) {
        return Result.ok(roleService.page(pageQuery.getCurrent(), pageQuery.getSize(), keyword));
    }

    @GetMapping("/{id}")
    public Result<RoleVO> detail(@PathVariable Long id) {
        return Result.ok(roleService.getDetail(id));
    }

    @PostMapping
    public Result<RoleVO> create(@Valid @RequestBody RoleCreateRequest request) {
        return Result.ok(roleService.create(request));
    }

    @PutMapping("/{id}")
    public Result<RoleVO> update(@PathVariable Long id, @Valid @RequestBody RoleUpdateRequest request) {
        return Result.ok(roleService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("sys:role:delete")
    public Result<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return Result.ok();
    }

    /** 全量覆盖分配权限 */
    @PostMapping("/{id}/permissions")
    public Result<Void> assignPermissions(@PathVariable Long id,
                                          @Valid @RequestBody AssignPermissionsRequest request) {
        roleService.assignPermissions(id, request.getPermissionIds());
        return Result.ok();
    }

    /** 查询角色已分配权限 ID */
    @GetMapping("/{id}/permissions")
    public Result<List<Long>> permissionIds(@PathVariable Long id) {
        return Result.ok(roleService.listPermissionIds(id));
    }
}
