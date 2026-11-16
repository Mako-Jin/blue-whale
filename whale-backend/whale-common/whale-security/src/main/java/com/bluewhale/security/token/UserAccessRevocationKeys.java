package com.bluewhale.security.token;

/**
 * 用户级访问撤销键约定（停用、删除、改密后即时失效该用户全部存量令牌）。
 *
 * <p>与租户成员解绑的键不同，用户状态是跨租户的：停用/删除/重置口令后，该用户在
 * 所有租户的 accessToken 都应立即失效，因此键不携带租户段：
 * {@code whale:auth:user:revoked:{userId}}，值为撤销时刻的 Unix 秒。网关与
 * whale-service-auth 共用该约定。</p>
 *
 * <p><b>Redis Cluster 槽位约定（P2-8）</b>：当前仅单键 {@code GET}/{@code SET}，
 * 不涉及跨键原子操作，按用户 ID 散列到不同槽位安全。若未来需要批量校验或把
 * 用户撤销键与租户成员键放进同一 Lua/MULTI，必须给动态段加统一 hash tag
 * （如 {@code whale:{auth:user:revoked}:<userId>}）；改键格式需 auth 与 gateway
 * 同批切换（双写过渡），禁止单侧先上导致撤销静默失效。</p>
 *
 * @author BlueWhale
 */
public final class UserAccessRevocationKeys {

    /**
     * 私有构造器：工具类，禁止实例化
     */
    private UserAccessRevocationKeys() {
    }

    /**
     * 构造用户级访问撤销缓存键
     *
     * @param userId 用户 ID
     * @return 缓存键
     */
    public static String revocationKey(Long userId) {
        return "whale:auth:user:revoked:" + userId;
    }
}
