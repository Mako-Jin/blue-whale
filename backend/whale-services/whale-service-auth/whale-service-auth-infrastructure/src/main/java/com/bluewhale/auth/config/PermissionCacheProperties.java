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
}
