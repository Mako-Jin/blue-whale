package com.bluewhale.auth.security;

import com.bluewhale.auth.jwt.JwtTokenProvider;
import com.bluewhale.security.degradation.SecurityDegradationAlerter;
import com.bluewhale.security.token.UserAccessRevocationKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的用户级访问撤销存储实现。
 *
 * <p>键结构由 {@link UserAccessRevocationKeys} 统一构造，值为撤销时刻的 Unix 秒；
 * TTL 取 accessToken 有效期 + 缓冲，覆盖撤销前签发的全部存量令牌。撤销后重新启用
 * 账号不会清除撤销时刻：旧令牌保持失效，重新登录签发的新令牌（iat 更晚）正常放行。</p>
 *
 * <p>降级策略与成员撤销一致：Redis 不可用时写入仅告警（权限缓存已失效、权限码为空），
 * 查询按未撤销放行，并输出 ERROR 级 {@code SECURITY_DEGRADATION} 告警。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisUserAccessRevocationStore implements UserAccessRevocationStore {

    /** 毫秒换算秒的进制 */
    private static final long MILLIS_PER_SECOND = 1000L;

    /** 最小写入 TTL（秒），避免出现 TTL<=0 导致键永不过期 */
    private static final long MIN_TTL_SECONDS = 1L;

    /** 在 accessToken 有效期之上追加的缓冲秒数，覆盖时钟偏差与边界令牌 */
    private static final long TTL_BUFFER_SECONDS = 60L;

    /** 降级告警场景标识：写入撤销时间 */
    private static final String SCENE_REVOKE = "auth:user-access-revocation:revoke";

    /** 降级告警场景标识：查询撤销时间 */
    private static final String SCENE_CHECK = "auth:user-access-revocation:check";

    private final StringRedisTemplate redisTemplate;
    private final JwtTokenProvider tokenProvider;

    @Override
    public void revoke(Long userId) {
        if (userId == null) {
            return;
        }
        long revokedAtSeconds = System.currentTimeMillis() / MILLIS_PER_SECOND;
        long ttlSeconds = Math.max(tokenProvider.getAccessExpireSeconds() + TTL_BUFFER_SECONDS,
                MIN_TTL_SECONDS);
        try {
            redisTemplate.opsForValue().set(UserAccessRevocationKeys.revocationKey(userId),
                    String.valueOf(revokedAtSeconds), ttlSeconds, TimeUnit.SECONDS);
        } catch (RuntimeException e) {
            log.warn("写入用户级撤销时间失败（Redis 不可用），存量令牌无法即时失效: userId={}, reason={}",
                    userId, e.getMessage());
            SecurityDegradationAlerter.alarm(SCENE_REVOKE,
                    "写入用户级撤销时间失败（Redis 不可用），存量令牌无法即时失效", e);
        }
    }

    @Override
    public boolean isRevoked(Long userId, Date issuedAt) {
        if (userId == null || issuedAt == null) {
            return false;
        }
        try {
            String revokedAt = redisTemplate.opsForValue()
                    .get(UserAccessRevocationKeys.revocationKey(userId));
            if (revokedAt == null) {
                return false;
            }
            return issuedAt.getTime() / MILLIS_PER_SECOND <= Long.parseLong(revokedAt);
        } catch (RuntimeException e) {
            log.warn("查询用户级撤销时间失败（Redis 不可用或值非法），按未撤销处理: userId={}, reason={}",
                    userId, e.getMessage());
            SecurityDegradationAlerter.alarm(SCENE_CHECK,
                    "查询用户级撤销时间失败（Redis 不可用或值非法），按未撤销处理", e);
            return false;
        }
    }
}
