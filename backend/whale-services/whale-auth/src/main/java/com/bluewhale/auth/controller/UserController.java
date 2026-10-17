package com.bluewhale.auth.controller;

import com.bluewhale.auth.dto.AssignRolesRequest;
import com.bluewhale.auth.dto.ChangePasswordRequest;
import com.bluewhale.auth.dto.UpdateAttrsRequest;
import com.bluewhale.auth.dto.UserCreateRequest;
import com.bluewhale.auth.dto.UserStatusRequest;
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

    /** 更新用户：参数可能含密码，落库前由脱敏器置为掩码 */
    @PutMapping("/{id}")
    @RequirePermission("sys:user:edit")
    @AuditLog(operation = "更新用户", type = OperateType.UPDATE, targetType = "sys_user", targetId = "#id")
    public Result<UserVO> update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
        return Result.ok(userService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("sys:user:delete")
    @AuditLog(operation = "删除用户", type = OperateType.DELETE, targetType = "sys_user", targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.ok();
    }

    /** 重置用户密码：凭据类操作，参数脱敏不落库（recordParams=false） */
    @PutMapping("/{id}/password")
    @RequirePermission("sys:user:resetPwd")
    @AuditLog(operation = "重置用户密码", type = OperateType.UPDATE, targetType = "sys_user",
            targetId = "#id", recordParams = false)
    public Result<Void> changePassword(@PathVariable Long id, @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(id, request);
        return Result.ok();
    }

    /** 启停用户：变更账号状态属敏感操作，必留痕 */
    @PutMapping("/{id}/status")
    @RequirePermission("sys:user:status")
    @AuditLog(operation = "启停用户", type = OperateType.UPDATE, targetType = "sys_user", targetId = "#id")
    public Result<Void> changeStatus(@PathVariable Long id, @Valid @RequestBody UserStatusRequest request) {
        userService.changeStatus(id, request);
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

    /** ABAC 属性写入（全量覆盖）：影响数据范围判定，属敏感操作必留痕 */
    @PutMapping("/{id}/attrs")
    @RequirePermission("sys:user:edit")
    @AuditLog(operation = "更新用户属性", type = OperateType.UPDATE, targetType = "sys_user", targetId = "#id")
    public Result<Void> updateAttrs(@PathVariable Long id, @Valid @RequestBody UpdateAttrsRequest request) {
        userPermissionService.replaceUserAttrs(id, request);
        return Result.ok();
    }
}
