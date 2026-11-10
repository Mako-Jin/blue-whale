package com.bluewhale.auth.application.auth.port;

import com.bluewhale.auth.application.auth.AuthenticatedUser;

import java.util.Optional;

/**
 * 用户会话查询端口：按全局用户 ID 与目标租户加载并装配角色。
 *
 * @author BlueWhale
 */
public interface UserSessionQuery {

    /**
     * 按用户 ID 加载指定租户下的用户快照
     *
     * @param userId 全局用户 ID
     * @param tenantId 目标租户 ID
     * @return 用户快照；不存在时返回空
     */
    Optional<AuthenticatedUser> loadUserById(Long userId, Long tenantId);
}
