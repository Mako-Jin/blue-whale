package com.bluewhale.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 权限判定缓存配置属性。
 * <p>{@code whale.auth.permission-cache.enabled=false} 时回退直查 DB（秒级回滚开关）；
 * {@code ttl} 为正常值存活时长，写入时由 {@code CacheProperties.ttlJitterPercent} 叠加随机抖动防雪崩。</p>
 *
 * @author BlueWhale
 */
@Data
@Component
@ConfigurationProperties(prefix = "whale.auth.permission-cache")
public class PermissionCacheProperties {

    /** 是否启用权限判定缓存，默认启用；置 false 即回到直查 DB */
    private boolean enabled = true;

    /** 权限码缓存正常值 TTL，默认 5 分钟 */
    private Duration ttl = Duration.ofMinutes(5);

    /** 是否启用跨实例失效广播（Redisson RTopic），默认启用；关闭后仅依赖共享 Redis 删除 */
    private boolean broadcastEnabled = true;

    /** 是否启用 Redis 断连后的待失效重对齐任务，默认启用 */
    private boolean reconcileEnabled = true;

    /** 重对齐执行间隔（毫秒），默认 30 秒 */
    private long reconcileIntervalMs = 30_000L;

    /** 重对齐首次延迟（毫秒），默认 10 秒 */
    private long reconcileInitialDelayMs = 10_000L;

    /** 单次重对齐最多处理的待失效用户数，默认 500 */
    private int reconcileBatchSize = 500;

    /** 待失效用户上限，防止 Redis 长时间不可用导致内存膨胀，默认 10000 */
    private int pendingLimit = 10_000;
}
