package com.bluewhale.auth.controller;

import com.bluewhale.auth.dto.AssignRolesRequest;
import com.bluewhale.auth.dto.UpdateAttrsRequest;
import com.bluewhale.auth.dto.UserCreateRequest;
import com.bluewhale.auth.dto.UserUpdateRequest;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.auth.service.SysUserPermissionService;
import com.bluewhale.auth.service.SysUserService;
import com.bluewhale.auth.vo.UserAttrVO;
import com.bluewhale.auth.vo.UserVO;
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
 * 用户管理：CRUD / 分配角色 / ABAC 属性读写
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final SysUserService userService;
    private final SysUserPermissionService userPermissionService;

    @GetMapping("/page")
    public Result<PageResult<UserVO>> page(PageQuery pageQuery,
                                           @RequestParam(required = false) String keyword) {
        return Result.ok(userService.page(pageQuery.getCurrent(), pageQuery.getSize(), keyword));
    }

    @GetMapping("/{id}")
    public Result<UserVO> detail(@PathVariable Long id) {
        return Result.ok(userService.getDetail(id));
    }

    @PostMapping
    @RequirePermission("sys:user:add")
    public Result<UserVO> create(@Valid @RequestBody UserCreateRequest request) {
        return Result.ok(userService.create(request));
    }

    @PutMapping("/{id}")
    public Result<UserVO> update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
        return Result.ok(userService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("sys:user:delete")
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.ok();
    }

    /** 全量覆盖分配角色 */
    @PostMapping("/{id}/roles")
    public Result<Void> assignRoles(@PathVariable Long id, @Valid @RequestBody AssignRolesRequest request) {
        userService.assignRoles(id, request.getRoleIds());
        return Result.ok();
    }

    /** 查询用户已分配角色 ID */
    @GetMapping("/{id}/roles")
    public Result<List<Long>> roleIds(@PathVariable Long id) {
        return Result.ok(userService.listRoleIds(id));
    }

    /** ABAC 属性读取 */
    @GetMapping("/{id}/attrs")
    public Result<List<UserAttrVO>> attrs(@PathVariable Long id) {
        return Result.ok(userPermissionService.listByUserId(id));
    }

    /** ABAC 属性写入（全量覆盖） */
    @PutMapping("/{id}/attrs")
    public Result<Void> updateAttrs(@PathVariable Long id, @Valid @RequestBody UpdateAttrsRequest request) {
        userPermissionService.replaceUserAttrs(id, request);
        return Result.ok();
    }
}
