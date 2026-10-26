package com.bluewhale.gateway.security;

import com.bluewhale.security.degradation.SecurityDegradationAlerter;
import com.bluewhale.security.token.TenantStatusKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 网关侧租户状态校验（Wave 5.54 Phase 2）。
 *
 * <p>whale-service-auth 在租户启停时写入 {@code whale:{tenantId}:cache:auth:tenant:status}，
 * 网关在 JWT 验签后读取：值为 {@code 0} 视为停用，立即拒绝存量 accessToken。</p>
 *
 * <p>降级策略：缓存缺失或 Redis 不可用时按「启用」放行（登录/刷新已做数据库校验），
 * 避免缓存故障阻断全站；异常时输出安全降级告警。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class TenantStatusChecker {

    /** 降级告警场景标识：网关校验租户状态 */
    private static final String SCENE_CHECK = "gateway:tenant-status:check";

    private final ReactiveStringRedisTemplate redisTemplate;

    /**
     * 构造校验器，注入响应式 Redis 模板
     *
     * @param redisTemplate 响应式字符串 Redis 模板
     */
    public TenantStatusChecker(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 判断租户是否已停用
     *
     * @param tenantId 租户 ID 文本（来自 JWT claim）
     * @return 已停用返回 {@code true}；缓存缺失或 Redis 不可用时返回 {@code false}（降级放行）
     */
    public Mono<Boolean> isDisabled(String tenantId) {
        Long id;
        try {
            id = Long.parseLong(tenantId);
        } catch (NumberFormatException e) {
            return Mono.just(Boolean.FALSE);
        }
        return redisTemplate.opsForValue()
                .get(TenantStatusKeys.statusKey(id))
                .map(TenantStatusKeys.DISABLED::equals)
                .defaultIfEmpty(Boolean.FALSE)
                .onErrorResume(e -> {
                    log.warn("网关查询租户状态失败（Redis 不可用），按启用放行: tenantId={}, reason={}",
                            tenantId, e.getMessage());
                    SecurityDegradationAlerter.alarm(SCENE_CHECK,
                            "网关查询租户状态失败（Redis 不可用），按启用放行", e);
                    return Mono.just(Boolean.FALSE);
                });
    }
}
