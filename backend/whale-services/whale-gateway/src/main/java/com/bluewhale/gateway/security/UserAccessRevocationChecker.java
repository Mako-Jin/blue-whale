package com.bluewhale.gateway.security;

import com.bluewhale.security.degradation.SecurityDegradationAlerter;
import com.bluewhale.security.token.UserAccessRevocationKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Date;

/**
 * 网关侧用户级访问撤销校验（停用、删除、改密后即时拒绝存量令牌）。
 *
 * <p>whale-service-auth 在用户停用、删除或重置口令后写入
 * {@code whale:auth:user:revoked:{userId}}，值为撤销时刻的 Unix 秒。该键跨租户生效，
 * 因此网关在成员撤销校验之后再校验一次：令牌 {@code iat} 不晚于撤销时刻即 401，
 * 避免停用/删除用户的旧令牌继续访问无权限注解的业务接口。</p>
 *
 * <p>降级策略与成员撤销一致：缓存缺失或 Redis 不可用时按「未撤销」放行，并输出
 * 安全降级告警。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class UserAccessRevocationChecker {

    /** 毫秒换算秒的进制 */
    private static final long MILLIS_PER_SECOND = 1000L;

    /** 降级告警场景标识：网关校验用户级撤销 */
    private static final String SCENE_CHECK = "gateway:user-access-revocation:check";

    private final ReactiveStringRedisTemplate redisTemplate;

    /**
     * 构造校验器，注入响应式 Redis 模板
     *
     * @param redisTemplate 响应式字符串 Redis 模板
     */
    public UserAccessRevocationChecker(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 判断令牌是否在用户级撤销前签发
     *
     * @param userId 令牌用户 ID 文本（来自 JWT claim）
     * @param issuedAt 令牌签发时间（JWT {@code iat}），缺失时按未撤销处理
     * @return 已撤销返回 {@code true}；缓存缺失或 Redis 不可用时返回 {@code false}（降级放行）
     */
    public Mono<Boolean> isRevoked(String userId, Date issuedAt) {
        Long parsedUserId = parseLong(userId);
        if (parsedUserId == null || issuedAt == null) {
            return Mono.just(Boolean.FALSE);
        }
        long issuedAtSeconds = issuedAt.getTime() / MILLIS_PER_SECOND;
        return redisTemplate.opsForValue()
                .get(UserAccessRevocationKeys.revocationKey(parsedUserId))
                .map(revokedAt -> Long.parseLong(revokedAt) >= issuedAtSeconds)
                .defaultIfEmpty(Boolean.FALSE)
                .onErrorResume(e -> {
                    log.warn("网关查询用户级撤销时间失败（Redis 不可用或值非法），按未撤销放行: "
                            + "userId={}, reason={}", userId, e.getMessage());
                    SecurityDegradationAlerter.alarm(SCENE_CHECK,
                            "网关查询用户级撤销时间失败（Redis 不可用或值非法），按未撤销放行", e);
                    return Mono.just(Boolean.FALSE);
                });
    }

    /**
     * 解析数值文本
     *
     * @param value 待解析文本
     * @return 数值；缺失或非法时返回 {@code null}
     */
    private static Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
