package com.bluewhale.security.token;

/**
 * 租户成员关系撤销键约定（成员解绑后即时失效存量令牌）。
 *
 * <p>whale-service-auth 在平台解除用户与租户关系时写入撤销时间戳，网关在验签后读取并拒绝
 * 签发时间不晚于该时间戳的 accessToken；键格式与 {@code TenantKeys} 保持一致：
 * {@code whale:{tenantId}:cache:auth:member:revoked:{userId}}。</p>
 *
 * @author BlueWhale
 */
public final class TenantMembershipRevocationKeys {

    /**
     * 私有构造器：工具类，禁止实例化
     */
    private TenantMembershipRevocationKeys() {
    }

    /**
     * 构造成员关系撤销缓存键
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @return 缓存键
     */
    public static String revocationKey(Long tenantId, Long userId) {
        return "whale:" + tenantId + ":cache:auth:member:revoked:" + userId;
    }
}
