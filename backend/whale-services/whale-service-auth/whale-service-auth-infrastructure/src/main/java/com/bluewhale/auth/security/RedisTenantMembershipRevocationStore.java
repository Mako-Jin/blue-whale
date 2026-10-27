package com.bluewhale.auth.security;

import com.bluewhale.auth.jwt.JwtTokenProvider;
import com.bluewhale.security.degradation.SecurityDegradationAlerter;
import com.bluewhale.security.token.TenantMembershipRevocationKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的租户成员关系撤销存储实现。
 *
 * <p>键结构由 {@link TenantMembershipRevocationKeys} 统一构造，值为撤销时刻的 Unix 秒；
 * 键 TTL 取 accessToken 有效期 + 缓冲，覆盖所有在撤销前签发的存量令牌，到期后无需保留。
 * 网关与 whale-service-auth 共用同一键约定，保证解绑写入与两侧校验指向同一键。</p>
 *
 * <p>降级策略：Redis 不可用时 {@link #revoke} 仅告警不抛异常（角色关联已删除，权限码随即失效），
 * {@link #isRevoked} 返回未撤销，保证鉴权链路可用；两处降级分支同时经
 * {@link SecurityDegradationAlerter} 输出 ERROR 级 {@code SECURITY_DEGRADATION} 告警。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisTenantMembershipRevocationStore implements TenantMembershipRevocationStore {

    /** 毫秒换算秒的进制 */
    private static final long MILLIS_PER_SECOND = 1000L;

    /** 最小写入 TTL（秒），避免出现 TTL<=0 导致键永不过期 */
    private static final long MIN_TTL_SECONDS = 1L;

    /** 在 accessToken 有效期之上追加的缓冲秒数，覆盖时钟偏差与边界令牌 */
    private static final long TTL_BUFFER_SECONDS = 60L;

    /** 降级告警场景标识：写入撤销时间 */
    private static final String SCENE_REVOKE = "auth:tenant-member-revocation:revoke";

    /** 降级告警场景标识：查询撤销时间 */
    private static final String SCENE_CHECK = "auth:tenant-member-revocation:check";

    private final StringRedisTemplate redisTemplate;
    private final JwtTokenProvider tokenProvider;

    @Override
    public void revoke(Long tenantId, Long userId) {
        if (tenantId == null || userId == null) {
            return;
        }
        long revokedAtSeconds = System.currentTimeMillis() / MILLIS_PER_SECOND;
        long ttlSeconds = Math.max(tokenProvider.getAccessExpireSeconds() + TTL_BUFFER_SECONDS,
                MIN_TTL_SECONDS);
        try {
            redisTemplate.opsForValue().set(
                    TenantMembershipRevocationKeys.revocationKey(tenantId, userId),
                    String.valueOf(revokedAtSeconds), ttlSeconds, TimeUnit.SECONDS);
        } catch (RuntimeException e) {
            log.warn("写入租户成员撤销时间失败（Redis 不可用），存量令牌无法即时失效: "
                    + "tenantId={}, userId={}, reason={}", tenantId, userId, e.getMessage());
            SecurityDegradationAlerter.alarm(SCENE_REVOKE,
                    "写入租户成员撤销时间失败（Redis 不可用），存量令牌无法即时失效", e);
        }
    }

    @Override
    public boolean isRevoked(Long tenantId, Long userId, Date issuedAt) {
        if (tenantId == null || userId == null || issuedAt == null) {
            return false;
        }
        try {
            String revokedAt = redisTemplate.opsForValue()
                    .get(TenantMembershipRevocationKeys.revocationKey(tenantId, userId));
            if (revokedAt == null) {
                return false;
            }
            return issuedAt.getTime() / MILLIS_PER_SECOND <= Long.parseLong(revokedAt);
        } catch (RuntimeException e) {
            log.warn("查询租户成员撤销时间失败（Redis 不可用或值非法），按未撤销处理: "
                    + "tenantId={}, userId={}, reason={}", tenantId, userId, e.getMessage());
            SecurityDegradationAlerter.alarm(SCENE_CHECK,
                    "查询租户成员撤销时间失败（Redis 不可用或值非法），按未撤销处理", e);
            return false;
        }
    }
}
