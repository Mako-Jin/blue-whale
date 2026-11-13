package com.bluewhale.auth.interfaces.api;

import com.bluewhale.auth.interfaces.dto.request.SaveUserProfileRequest;
import com.bluewhale.auth.interfaces.dto.response.UserProfileResponse;
import com.bluewhale.common.core.result.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 用户身份档案 API 契约（权限中心扩展 M3）。
 *
 * <p>档案读端返回脱敏视图（证件号 / 电话 / 联系方式掩码）；证件号明文须另持
 * {@code sys:user:profile:credential-view} 权限走专用端点。子集合整体替换：保存请求即为目标状态。</p>
 *
 * @author BlueWhale
 */
@RequestMapping(path = "/user-profile/v{version}", version = "1")
public interface UserProfileApi {

    /**
     * 查询用户身份档案（脱敏视图）
     *
     * @param userId 用户 ID
     * @return 脱敏档案视图
     */
    @GetMapping("/{userId}/view")
    Result<UserProfileResponse> view(@PathVariable Long userId);

    /**
     * 保存用户身份档案（无档案则建，有则整体更新子集合）
     *
     * @param userId 用户 ID
     * @param request 保存请求（证件号 / 密保答案为明文，应用服务加密）
     * @return 保存后的脱敏视图
     */
    @PutMapping("/{userId}")
    Result<UserProfileResponse> save(@PathVariable Long userId,
                                     @Valid @RequestBody SaveUserProfileRequest request);

    /**
     * 删除用户身份档案（逻辑删除主表并清理子表）
     *
     * @param userId 用户 ID
     * @return 操作结果
     */
    @DeleteMapping("/{userId}")
    Result<Void> delete(@PathVariable Long userId);

    /**
     * 查看证件号明文（专用权限，产生 QUERY 审计且不记录返回结果）
     *
     * @param userId 用户 ID
     * @param credentialId 证件条目主键
     * @return 证件号明文
     */
    @GetMapping("/{userId}/credentials/{credentialId}")
    Result<String> viewCredential(@PathVariable Long userId, @PathVariable Long credentialId);
}
