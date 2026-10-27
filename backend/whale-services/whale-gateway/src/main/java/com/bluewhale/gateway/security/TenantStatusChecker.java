package com.bluewhale.gateway.security;

import com.bluewhale.security.degradation.SecurityDegradationAlerter;
import com.bluewhale.security.token.TenantStatusKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 网关侧租户可用性校验（Wave 5.54 Phase 2）。
 *
 * <p>whale-service-auth 在租户开通、启停、续期时写入两个键，网关在 JWT 验签后一次读取：</p>
 * <ul>
 *   <li>{@code whale:{tenantId}:cache:auth:tenant:status}：{@code 0} 为停用</li>
 *   <li>{@code whale:{tenantId}:cache:auth:tenant:expire}：到期 Unix 秒，缺失表示长期有效</li>
 * </ul>
 *
 * <p>停用或当前时间已达到到期秒数时拒绝存量 accessToken。由于到期时间在使用时比较，
 * 无需定时任务即可在到期瞬间生效；续期由 auth 侧同步刷新缓存。</p>
 *
 * <p>降级策略：缓存缺失或 Redis 不可用时按「可用」放行（登录/刷新已做数据库校验），
 * 避免缓存故障阻断全站；异常时输出安全降级告警。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class TenantStatusChecker {

    /** 毫秒换算秒的进制 */
    private static final long MILLIS_PER_SECOND = 1000L;

    /** 缓存缺失时占位下标：到期键在列表中的位置 */
    private static final int EXPIRE_INDEX = 1;

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
     * 判断租户是否不可用（已停用或已到期）
     *
     * @param tenantId 租户 ID 文本（来自 JWT claim）
     * @return 不可用返回 {@code true}；缓存缺失或 Redis 不可用时返回 {@code false}（降级放行）
     */
    public Mono<Boolean> isUnavailable(String tenantId) {
        Long id = parseLong(tenantId);
        if (id == null) {
            return Mono.just(Boolean.FALSE);
        }
        long nowSeconds = System.currentTimeMillis() / MILLIS_PER_SECOND;
        List<String> keys = List.of(TenantStatusKeys.statusKey(id), TenantStatusKeys.expireKey(id));
        return redisTemplate.opsForValue()
                .multiGet(keys)
                .map(values -> isDisabled(values) || isExpired(values, nowSeconds))
                .defaultIfEmpty(Boolean.FALSE)
                .onErrorResume(e -> {
                    log.warn("网关查询租户状态失败（Redis 不可用），按可用放行: tenantId={}, reason={}",
                            tenantId, e.getMessage());
                    SecurityDegradationAlerter.alarm(SCENE_CHECK,
                            "网关查询租户状态失败（Redis 不可用），按可用放行", e);
                    return Mono.just(Boolean.FALSE);
                });
    }

    /**
     * 判断状态键是否为停用
     *
     * @param values 状态与到期时间缓存值
     * @return 停用返回 {@code true}
     */
    private static boolean isDisabled(List<String> values) {
        return values != null && !values.isEmpty()
                && TenantStatusKeys.DISABLED.equals(values.get(0));
    }

    /**
     * 判断当前时间是否已达到缓存中的到期秒数
     *
     * @param values 状态与到期时间缓存值
     * @param nowSeconds 当前 Unix 秒
     * @return 已到期返回 {@code true}；到期键缺失或值非法时返回 {@code false}
     */
    private static boolean isExpired(List<String> values, long nowSeconds) {
        if (values == null || values.size() <= EXPIRE_INDEX) {
            return false;
        }
        String expireAt = values.get(EXPIRE_INDEX);
        if (expireAt == null || expireAt.isBlank()) {
            return false;
        }
        try {
            return Long.parseLong(expireAt.trim()) <= nowSeconds;
        } catch (NumberFormatException e) {
            log.warn("租户到期时间缓存值非法，按未到期放行: value={}", expireAt);
            return false;
        }
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
