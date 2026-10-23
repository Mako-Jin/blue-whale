package com.bluewhale.auth.controller;

import com.bluewhale.auth.application.user.UserAppService;
import com.bluewhale.auth.application.user.command.AssignUserRolesCommand;
import com.bluewhale.auth.application.user.command.ChangeUserPasswordCommand;
import com.bluewhale.auth.application.user.command.ChangeUserStatusCommand;
import com.bluewhale.auth.application.user.command.CreateUserCommand;
import com.bluewhale.auth.application.user.command.UpdateUserCommand;
import com.bluewhale.auth.application.user.query.UserPageQuery;
import com.bluewhale.auth.application.userpermission.UserPermissionAppService;
import com.bluewhale.auth.application.userpermission.command.ReplaceUserAttrsCommand;
import com.bluewhale.auth.config.DddMigrationProperties;
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
import com.bluewhale.common.idempotent.annotation.Idempotent;
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
 * <p>迁移期双路径：{@code whale.auth.ddd.user=true} 时走 {@code application} 层 AppService，
 * 否则走既有 {@code Sys*Service}（与迁移前行为一致）；REST 路径与出入参保持不变。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final SysUserService userService;
    private final SysUserPermissionService userPermissionService;
    private final UserAppService userAppService;
    private final UserPermissionAppService userPermissionAppService;
    private final DddMigrationProperties dddMigrationProperties;

    /**
     * 分页查询用户
     *
     * @param pageQuery 分页参数（当前页与每页条数）
     * @param keyword 用户名/昵称/手机号模糊匹配关键字（可空）
     * @return 用户分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:user:list")
    public Result<PageResult<UserVO>> page(PageQuery pageQuery,
                                           @RequestParam(required = false) String keyword) {
        if (dddMigrationProperties.isUser()) {
            return Result.ok(userAppService.page(
                    new UserPageQuery(pageQuery.getCurrent(), pageQuery.getSize(), keyword)));
        }
        return Result.ok(userService.page(pageQuery.getCurrent(), pageQuery.getSize(), keyword));
    }

    /**
     * 用户详情
     *
     * @param id 用户 ID
     * @return 用户视图对象（不含密码字段）
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:user:list")
    public Result<UserVO> detail(@PathVariable Long id) {
        return Result.ok(dddMigrationProperties.isUser() ? userAppService.getDetail(id) : userService.getDetail(id));
    }

    /**
     * 创建用户：参数含密码，落库前由脱敏器置为掩码
     *
     * @param request 用户创建请求参数
     * @return 新建用户视图对象
     */
    @PostMapping
    @RequirePermission("sys:user:add")
    @AuditLog(operation = "创建用户", type = OperateType.CREATE, targetType = "sys_user")
    @Idempotent(biz = "auth:user:create", key = "#request.username")
    public Result<UserVO> create(@Valid @RequestBody UserCreateRequest request) {
        if (dddMigrationProperties.isUser()) {
            return Result.ok(userAppService.create(new CreateUserCommand(request.getUsername(),
                    request.getPassword(), request.getNickname(), request.getPhone(), request.getEmail(),
                    request.getAvatar(), request.getDeptId(), request.getStatus())));
        }
        return Result.ok(userService.create(request));
    }

    /**
     * 更新用户：参数可能含密码，落库前由脱敏器置为掩码
     *
     * @param id 用户 ID
     * @param request 用户更新请求参数
     * @return 更新后的用户视图对象
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:user:edit")
    @AuditLog(operation = "更新用户", type = OperateType.UPDATE, targetType = "sys_user", targetId = "#id")
    public Result<UserVO> update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
        if (dddMigrationProperties.isUser()) {
            return Result.ok(userAppService.update(id, new UpdateUserCommand(request.getPassword(),
                    request.getNickname(), request.getPhone(), request.getEmail(), request.getAvatar(),
                    request.getDeptId(), request.getStatus())));
        }
        return Result.ok(userService.update(id, request));
    }

    /**
     * 删除用户
     *
     * @param id 用户 ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:user:delete")
    @AuditLog(operation = "删除用户", type = OperateType.DELETE, targetType = "sys_user", targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        if (dddMigrationProperties.isUser()) {
            userAppService.delete(id);
        } else {
            userService.delete(id);
        }
        return Result.ok();
    }

    /**
     * 重置用户密码：凭据类操作，参数脱敏不落库（recordParams=false）
     *
     * @param id 用户 ID
     * @param request 密码重置请求（含新密码）
     * @return 操作结果
     */
    @PutMapping("/{id}/password")
    @RequirePermission("sys:user:resetPwd")
    @AuditLog(operation = "重置用户密码", type = OperateType.UPDATE, targetType = "sys_user",
            targetId = "#id", recordParams = false)
    public Result<Void> changePassword(@PathVariable Long id, @Valid @RequestBody ChangePasswordRequest request) {
        if (dddMigrationProperties.isUser()) {
            userAppService.changePassword(id, new ChangeUserPasswordCommand(request.getPassword()));
        } else {
            userService.changePassword(id, request);
        }
        return Result.ok();
    }

    /**
     * 启停用户：变更账号状态属敏感操作，必留痕
     *
     * @param id 用户 ID
     * @param request 状态变更请求（含目标状态）
     * @return 操作结果
     */
    @PutMapping("/{id}/status")
    @RequirePermission("sys:user:status")
    @AuditLog(operation = "启停用户", type = OperateType.UPDATE, targetType = "sys_user", targetId = "#id")
    public Result<Void> changeStatus(@PathVariable Long id, @Valid @RequestBody UserStatusRequest request) {
        if (dddMigrationProperties.isUser()) {
            userAppService.changeStatus(id, new ChangeUserStatusCommand(request.getStatus()));
        } else {
            userService.changeStatus(id, request);
        }
        return Result.ok();
    }

    /**
     * 全量覆盖分配角色：授权变更属敏感操作，必留痕
     *
     * @param id 用户 ID
     * @param request 分配角色请求（含角色 ID 列表）
     * @return 操作结果
     */
    @PostMapping("/{id}/roles")
    @RequirePermission("sys:user:assign")
    @AuditLog(operation = "分配用户角色", type = OperateType.GRANT, targetType = "sys_user", targetId = "#id")
    public Result<Void> assignRoles(@PathVariable Long id, @Valid @RequestBody AssignRolesRequest request) {
        if (dddMigrationProperties.isUser()) {
            userAppService.assignRoles(id, new AssignUserRolesCommand(request.getRoleIds()));
        } else {
            userService.assignRoles(id, request.getRoleIds());
        }
        return Result.ok();
    }

    /**
     * 查询用户已分配角色 ID
     *
     * @param id 用户 ID
     * @return 用户已分配的角色 ID 列表
     */
    @GetMapping("/{id}/roles")
    @RequirePermission("sys:user:list")
    public Result<List<Long>> roleIds(@PathVariable Long id) {
        return Result.ok(dddMigrationProperties.isUser() ? userAppService.listRoleIds(id) : userService.listRoleIds(id));
    }

    /**
     * ABAC 属性读取
     *
     * @param id 用户 ID
     * @return 用户 ABAC 属性列表
     */
    @GetMapping("/{id}/attrs")
    @RequirePermission("sys:user:list")
    public Result<List<UserAttrVO>> attrs(@PathVariable Long id) {
        return Result.ok(dddMigrationProperties.isUser()
                ? userPermissionAppService.listByUserId(id)
                : userPermissionService.listByUserId(id));
    }

    /**
     * ABAC 属性写入（全量覆盖）：影响数据范围判定，属敏感操作必留痕
     *
     * @param id 用户 ID
     * @param request 属性写入请求（全量覆盖）
     * @return 操作结果
     */
    @PutMapping("/{id}/attrs")
    @RequirePermission("sys:user:edit")
    @AuditLog(operation = "更新用户属性", type = OperateType.UPDATE, targetType = "sys_user", targetId = "#id")
    public Result<Void> updateAttrs(@PathVariable Long id, @Valid @RequestBody UpdateAttrsRequest request) {
        if (dddMigrationProperties.isUser()) {
            List<ReplaceUserAttrsCommand.UserAttrCommand> attrs = request.getAttrs().stream()
                    .map(item -> new ReplaceUserAttrsCommand.UserAttrCommand(
                            item.getPermissionId(), item.getAttrKey(), item.getAttrValue()))
                    .toList();
            userPermissionAppService.replaceUserAttrs(id, new ReplaceUserAttrsCommand(attrs));
        } else {
            userPermissionService.replaceUserAttrs(id, request);
        }
        return Result.ok();
    }
}
