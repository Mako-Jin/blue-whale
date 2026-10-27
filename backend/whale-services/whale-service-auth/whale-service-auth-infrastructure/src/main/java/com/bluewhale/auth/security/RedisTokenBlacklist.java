package com.bluewhale.auth.security;

import com.bluewhale.auth.jwt.JwtTokenProvider;
import com.bluewhale.security.degradation.SecurityDegradationAlerter;
import com.bluewhale.security.degradation.SecurityDegradationPolicy;
import com.bluewhale.security.token.TokenBlacklistKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的令牌黑名单实现。
 * <p>键结构由 {@link TokenBlacklistKeys} 统一构造（{@code auth:token:blacklist:<sha256(token)>}），
 * 值为固定占位符，仅依赖键是否存在判断命中；使用摘要而非 token 原文作为键名，
 * 避免明文凭据落入 Redis keyspace。键约定与 whale-gateway 侧校验实现共用，
 * 保证登出写入与网关校验指向同一键。</p>
 * <p>降级策略：Redis 不可用时 {@link #add} 仅告警不抛异常，{@link #contains} 返回未命中，
 * 保证鉴权链路整体可用（与 whale-i18n-sdk 的 Redis 降级策略一致）。两处降级分支同时经
 * {@link SecurityDegradationAlerter} 输出 ERROR 级 {@code SECURITY_DEGRADATION} 告警并自增进程内计数。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisTokenBlacklist implements TokenBlacklist {

    /** 键值占位符：命中与否只看键是否存在 */
    private static final String PLACEHOLDER_VALUE = "1";

    /** 最小写入 TTL（秒），避免出现 TTL<=0 导致键永不过期 */
    private static final long MIN_TTL_SECONDS = 1L;

    /** 降级告警场景标识：写入黑名单 */
    private static final String SCENE_ADD = "auth:token-blacklist:add";

    /** 降级告警场景标识：查询黑名单 */
    private static final String SCENE_CONTAINS = "auth:token-blacklist:contains";

    private final StringRedisTemplate redisTemplate;
    private final JwtTokenProvider tokenProvider;

    @Override
    public void add(String token) {
        long ttlSeconds = tokenProvider.getRemainingValiditySeconds(token);
        if (ttlSeconds < MIN_TTL_SECONDS) {
            // token 已过期，本身不再可用，无需占用黑名单空间
            return;
        }
        try {
            redisTemplate.opsForValue()
                    .set(TokenBlacklistKeys.blacklistKey(token), PLACEHOLDER_VALUE, ttlSeconds, TimeUnit.SECONDS);
        } catch (RuntimeException e) {
            log.warn("令牌写入黑名单失败（Redis 不可用），本次登出退化为客户端丢弃 token: {}", e.getMessage());
            SecurityDegradationAlerter.alarm(SCENE_ADD, "令牌写入黑名单失败（Redis 不可用），本次登出退化为客户端丢弃 token", e);
        }
    }

    @Override
    public boolean contains(String token) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(TokenBlacklistKeys.blacklistKey(token)));
        } catch (RuntimeException e) {
            log.warn("令牌黑名单查询失败（Redis 不可用），按策略处理: {}", e.getMessage());
            SecurityDegradationAlerter.alarm(SCENE_CONTAINS, "令牌黑名单查询失败（Redis 不可用），按策略处理", e);
            return SecurityDegradationPolicy.failClosed();
        }
    }
}
