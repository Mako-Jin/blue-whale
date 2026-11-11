package com.bluewhale.auth.application.model.port;

/**
 * 二次认证（step-up）凭证存储端口
 *
 * <p>凭证与（租户, 用户）绑定，TTL 窗口内可复用；实现须保证比对恒时，
 * 存储不可用时 {@link #matches} 返回 {@code false}（fail-closed，危险操作宁拒绝）。</p>
 *
 * @author BlueWhale
 */
public interface ReauthTokenStore {

    /**
     * 发放新凭证并落存储（同用户旧凭证被覆盖）
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @return 新凭证（随机十六进制串）
     */
    String issue(Long tenantId, Long userId);

    /**
     * 校验凭证是否有效
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param token 待校验凭证
     * @return 有效期内匹配返回 {@code true}
     */
    boolean matches(Long tenantId, Long userId, String token);

    /**
     * 凭证有效期（秒），供前端展示剩余窗口
     *
     * @return 有效期秒数
     */
    long ttlSeconds();
}
