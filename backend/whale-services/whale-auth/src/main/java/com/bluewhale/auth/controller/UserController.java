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
    @RequirePermission("sys:user:list")
    public Result<PageResult<UserVO>> page(PageQuery pageQuery,
                                           @RequestParam(required = false) String keyword) {
        return Result.ok(userService.page(pageQuery.getCurrent(), pageQuery.getSize(), keyword));
    }

    @GetMapping("/{id}")
    @RequirePermission("sys:user:list")
    public Result<UserVO> detail(@PathVariable Long id) {
        return Result.ok(userService.getDetail(id));
    }

    /** 创建用户：参数含密码，落库前由脱敏器置为掩码 */
    @PostMapping
    @RequirePermission("sys:user:add")
    @AuditLog(operation = "创建用户", type = OperateType.CREATE, targetType = "sys_user")
    public Result<UserVO> create(@Valid @RequestBody UserCreateRequest request) {
        return Result.ok(userService.create(request));
    }

    @PutMapping("/{id}")
    @RequirePermission("sys:user:edit")
    public Result<UserVO> update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
        return Result.ok(userService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("sys:user:delete")
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.ok();
    }

    /** 全量覆盖分配角色：授权变更属敏感操作，必留痕 */
    @PostMapping("/{id}/roles")
    @RequirePermission("sys:user:assign")
    @AuditLog(operation = "分配用户角色", type = OperateType.GRANT, targetType = "sys_user", targetId = "#id")
    public Result<Void> assignRoles(@PathVariable Long id, @Valid @RequestBody AssignRolesRequest request) {
        userService.assignRoles(id, request.getRoleIds());
        return Result.ok();
    }

    /** 查询用户已分配角色 ID */
    @GetMapping("/{id}/roles")
    @RequirePermission("sys:user:list")
    public Result<List<Long>> roleIds(@PathVariable Long id) {
        return Result.ok(userService.listRoleIds(id));
    }

    /** ABAC 属性读取 */
    @GetMapping("/{id}/attrs")
    @RequirePermission("sys:user:list")
    public Result<List<UserAttrVO>> attrs(@PathVariable Long id) {
        return Result.ok(userPermissionService.listByUserId(id));
    }

    /** ABAC 属性写入（全量覆盖） */
    @PutMapping("/{id}/attrs")
    @RequirePermission("sys:user:edit")
    public Result<Void> updateAttrs(@PathVariable Long id, @Valid @RequestBody UpdateAttrsRequest request) {
        userPermissionService.replaceUserAttrs(id, request);
        return Result.ok();
    }
}
