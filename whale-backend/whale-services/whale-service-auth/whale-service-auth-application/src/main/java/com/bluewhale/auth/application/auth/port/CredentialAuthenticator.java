package com.bluewhale.auth.application.auth.port;

import com.bluewhale.auth.application.auth.AuthenticatedUser;

/**
 * 凭据认证端口：由基础设施适配器基于 Spring Security 实现，应用层只接收认证结果。
 *
 * @author BlueWhale
 */
public interface CredentialAuthenticator {

    /**
     * 校验用户名与明文密码
     *
     * @param tenantId 登录租户 ID
     * @param username 用户名
     * @param rawPassword 明文密码
     * @return 认证通过的用户快照
     */
    AuthenticatedUser authenticate(Long tenantId, String username, String rawPassword);
}
