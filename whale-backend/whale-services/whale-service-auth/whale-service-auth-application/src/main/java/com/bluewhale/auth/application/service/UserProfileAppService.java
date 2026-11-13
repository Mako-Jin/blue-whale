package com.bluewhale.auth.application.service;

import com.bluewhale.auth.application.model.command.SaveUserProfileCommand;
import com.bluewhale.auth.application.model.vo.UserProfileVO;

/**
 * 用户身份档案应用服务（权限中心扩展 M3）。
 *
 * <p>子集合整体替换口径：保存命令即为目标状态；接口层返回脱敏视图，
 * 证件明文需另持专用权限调用。</p>
 *
 * @author BlueWhale
 */
public interface UserProfileAppService {

    /**
     * 保存档案（无档案则建，有则更新并整体重建子表）
     *
     * @param command 保存命令（证件号 / 密保答案为明文，服务内加密）
     * @return 脱敏视图
     */
    UserProfileVO save(SaveUserProfileCommand command);

    /**
     * 按用户查询档案（脱敏视图）
     *
     * @param userId 用户 ID
     * @return 脱敏视图
     */
    UserProfileVO getByUserId(Long userId);

    /**
     * 查看证件号明文（专用权限 {@code sys:user:profile:credential-view}）
     *
     * @param userId 用户 ID
     * @param credentialId 证件条目主键
     * @return 证件号明文
     */
    String getCredentialPlain(Long userId, Long credentialId);

    /**
     * 标记联系方式已验证
     *
     * @param userId 用户 ID
     * @param contactId 联系方式条目主键
     */
    void markContactVerified(Long userId, Long contactId);

    /**
     * 删除档案（逻辑删除主表并清理子表）
     *
     * @param userId 用户 ID
     */
    void deleteByUserId(Long userId);
}
