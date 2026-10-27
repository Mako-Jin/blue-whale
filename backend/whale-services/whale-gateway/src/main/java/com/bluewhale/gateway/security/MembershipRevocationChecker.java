package com.bluewhale.gateway.security;

import com.bluewhale.security.degradation.SecurityDegradationAlerter;
import com.bluewhale.security.token.TenantMembershipRevocationKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Date;

/**
 * 网关侧租户成员关系撤销校验（成员解绑后即时拒绝存量令牌）。
 *
 * <p>whale-service-auth 在平台解除用户与租户关系时写入
 * {@code whale:{tenantId}:cache:auth:member:revoked:{userId}}，值为撤销时刻的 Unix 秒。
 * 网关在 JWT 验签后读取：令牌 {@code iat} 不晚于撤销时刻即视为已撤销，直接 401，
 * 避免解绑后旧 accessToken 在过期前继续访问目标租户。</p>
 *
 * <p>降级策略：缓存缺失或 Redis 不可用时按「未撤销」放行；异常时输出安全降级告警，
 * 与租户状态、登出黑名单校验的可用性优先取舍保持一致。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class MembershipRevocationChecker {

    /** 毫秒换算秒的进制 */
    private static final long MILLIS_PER_SECOND = 1000L;

    /** 降级告警场景标识：网关校验成员撤销 */
    private static final String SCENE_CHECK = "gateway:membership-revocation:check";

    private final ReactiveStringRedisTemplate redisTemplate;

    /**
     * 构造校验器，注入响应式 Redis 模板
     *
     * @param redisTemplate 响应式字符串 Redis 模板
     */
    public MembershipRevocationChecker(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 判断令牌是否在租户成员关系撤销前签发
     *
     * @param tenantId 令牌租户 ID 文本（来自 JWT claim）
     * @param userId 令牌用户 ID 文本（来自 JWT claim）
     * @param issuedAt 令牌签发时间（JWT {@code iat}），缺失时按未撤销处理
     * @return 已撤销返回 {@code true}；缓存缺失或 Redis 不可用时返回 {@code false}（降级放行）
     */
    public Mono<Boolean> isRevoked(String tenantId, String userId, Date issuedAt) {
        Long parsedTenantId = parseLong(tenantId);
        Long parsedUserId = parseLong(userId);
        if (parsedTenantId == null || parsedUserId == null || issuedAt == null) {
            return Mono.just(Boolean.FALSE);
        }
        long issuedAtSeconds = issuedAt.getTime() / MILLIS_PER_SECOND;
        return redisTemplate.opsForValue()
                .get(TenantMembershipRevocationKeys.revocationKey(parsedTenantId, parsedUserId))
                .map(revokedAt -> Long.parseLong(revokedAt) >= issuedAtSeconds)
                .defaultIfEmpty(Boolean.FALSE)
                .onErrorResume(e -> {
                    log.warn("网关查询租户成员撤销时间失败（Redis 不可用或值非法），按未撤销放行: "
                            + "tenantId={}, userId={}, reason={}", tenantId, userId, e.getMessage());
                    SecurityDegradationAlerter.alarm(SCENE_CHECK,
                            "网关查询租户成员撤销时间失败（Redis 不可用或值非法），按未撤销放行", e);
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
