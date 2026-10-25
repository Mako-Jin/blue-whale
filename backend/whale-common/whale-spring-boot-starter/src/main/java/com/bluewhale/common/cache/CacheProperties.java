package com.bluewhale.common.cache;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 缓存一致性配置属性。
 * <p>Key 约定 {@code whale:{tenantId}:cache:{name}:{id}}；空值以 {@code nullSentinel} 短 TTL 写入防穿透，
 * 正常值 TTL 叠加 ±{@code ttlJitterPercent}% 随机抖动防雪崩。</p>
 *
 * @author BlueWhale
 */
@Data
@ConfigurationProperties(prefix = "whale.cache")
public class CacheProperties {

    /** 空值哨兵串：命中该值视为缓存空结果，直接返回 {@code null} 且不再回源 */
    private String nullSentinel = "__NULL__";

    /** 正常值 TTL 的随机抖动百分比（正值），0 表示不抖动 */
    private int ttlJitterPercent = 10;

    /** 空值哨兵的存活时长（短 TTL，防穿透） */
    private Duration nullTtl = Duration.ofSeconds(60);
}
