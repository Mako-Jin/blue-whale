package com.bluewhale.auth.security;

import java.util.Date;

/**
 * 租户成员关系撤销存储端口（解绑后即时失效存量访问令牌）。
 *
 * <p>平台解除用户与租户关系时写入撤销时间戳；JWT 过滤器和网关按
 * {@code (tenantId, userId, 令牌签发时间)} 判定存量令牌是否已被撤销。
 * 存储故障时按「未撤销」降级，避免鉴权链路整体不可用（与登出黑名单取舍一致）。</p>
 *
 * @author BlueWhale
 */
public interface TenantMembershipRevocationStore {

    /**
     * 记录某用户在某租户的成员关系撤销时间（当前时间）
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     */
    void revoke(Long tenantId, Long userId);

    /**
     * 判断令牌是否在成员关系撤销前签发
     *
     * @param tenantId 令牌租户 ID
     * @param userId 令牌用户 ID
     * @param issuedAt 令牌签发时间
     * @return 已撤销且令牌签发时间不晚于撤销时间时返回 {@code true}
     */
    boolean isRevoked(Long tenantId, Long userId, Date issuedAt);
}
