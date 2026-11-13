package com.bluewhale.auth.starter.controller;

import com.bluewhale.auth.application.model.command.SaveUserProfileCommand;
import com.bluewhale.auth.application.service.UserProfileAppService;
import com.bluewhale.auth.interfaces.api.UserProfileApi;
import com.bluewhale.auth.interfaces.dto.request.SaveUserProfileRequest;
import com.bluewhale.auth.interfaces.dto.response.UserProfileResponse;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.auth.starter.assemble.UserProfileAssembler;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户身份档案接口实现（Web 适配层）：协议适配 + DTO ↔ Command 转换，业务编排在 application 层。
 *
 * @author BlueWhale
 */
@RestController
@RequiredArgsConstructor
public class UserProfileController implements UserProfileApi {

    private final UserProfileAppService userProfileAppService;
    private final UserProfileAssembler userProfileAssembler;

    /**
     * 查询用户身份档案（脱敏视图）
     *
     * @param userId 用户 ID
     * @return 脱敏档案视图
     */
    @Override
    @RequirePermission("sys:user:profile:view")
    public Result<UserProfileResponse> view(@PathVariable Long userId) {
        return Result.ok(userProfileAssembler.toResponse(userProfileAppService.getByUserId(userId)));
    }

    /**
     * 保存用户身份档案（整体替换子集合；证件号 / 密保答案明文由应用服务加密）
     *
     * @param userId 用户 ID
     * @param request 保存请求
     * @return 保存后的脱敏视图
     */
    @Override
    @RequirePermission("sys:user:profile:save")
    @AuditLog(operation = "保存用户身份档案", type = OperateType.UPDATE, targetType = "sys_user_profile",
            targetId = "#userId")
    public Result<UserProfileResponse> save(@PathVariable Long userId,
                                            @Valid @RequestBody SaveUserProfileRequest request) {
        SaveUserProfileCommand command = userProfileAssembler.toCommand(userId, request);
        return Result.ok(userProfileAssembler.toResponse(userProfileAppService.save(command)));
    }

    /**
     * 删除用户身份档案（逻辑删除主表并清理子表）
     *
     * @param userId 用户 ID
     * @return 操作结果
     */
    @Override
    @RequirePermission("sys:user:profile:delete")
    @AuditLog(operation = "删除用户身份档案", type = OperateType.DELETE, targetType = "sys_user_profile",
            targetId = "#userId")
    public Result<Void> delete(@PathVariable Long userId) {
        userProfileAppService.deleteByUserId(userId);
        return Result.ok();
    }

    /**
     * 查看证件号明文（敏感读：QUERY 审计且不记录返回结果）
     *
     * @param userId 用户 ID
     * @param credentialId 证件条目主键
     * @return 证件号明文
     */
    @Override
    @RequirePermission("sys:user:profile:credential-view")
    @AuditLog(operation = "查看证件号明文", type = OperateType.QUERY, targetType = "sys_user_profile",
            targetId = "#userId", recordResult = false)
    public Result<String> viewCredential(@PathVariable Long userId, @PathVariable Long credentialId) {
        return Result.ok(userProfileAppService.getCredentialPlain(userId, credentialId));
    }
}
