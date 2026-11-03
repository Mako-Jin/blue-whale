package com.bluewhale.auth.application.auth.port;

import com.bluewhale.auth.application.auth.TokenPayload;

import java.util.List;

/**
 * 令牌签发与解析端口：具体 JWT 实现由基础设施适配器提供。
 *
 * @author BlueWhale
 */
public interface TokenIssuer {

    /**
     * 签发 accessToken
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param username 用户名
     * @param roles 角色 code 列表
     * @param pwdChangeRequired 是否需强制改密（REQ-SEC-023：携带 {@code pwdChangeRequired} claim）
     * @return accessToken 原文
     */
    String issueAccessToken(Long tenantId, Long userId, String username, List<String> roles,
                            boolean pwdChangeRequired);

    /**
     * 签发 accessToken（不带强制改密标记）
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param username 用户名
     * @param roles 角色 code 列表
     * @return accessToken 原文
     */
    default String issueAccessToken(Long tenantId, Long userId, String username, List<String> roles) {
        return issueAccessToken(tenantId, userId, username, roles, false);
    }

    /**
     * 签发 refreshToken
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param username 用户名
     * @param familyId 轮换 family 标识（可空）
     * @return refreshToken 原文
     */
    String issueRefreshToken(Long tenantId, Long userId, String username, String familyId);

    /**
     * accessToken 有效期（秒）
     *
     * @return 有效期秒数
     */
    long accessExpireSeconds();

    /**
     * refreshToken 有效期（秒）
     *
     * @return 有效期秒数
     */
    long refreshExpireSeconds();

    /**
     * 令牌剩余有效期（秒）
     *
     * @param token 令牌原文
     * @return 剩余秒数
     */
    long remainingValiditySeconds(String token);

    /**
     * 解析并校验令牌签名与有效期
     *
     * @param token 令牌原文
     * @return 令牌载荷；签名或有效期非法时返回 {@code null}
     */
    TokenPayload parse(String token);
}
