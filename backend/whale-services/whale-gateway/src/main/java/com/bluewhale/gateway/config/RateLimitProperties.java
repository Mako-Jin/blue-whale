package com.bluewhale.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 网关入口限流配置属性（T5.4 / AUTH-30）。
 * <p>基于 Redis 计数器的固定窗口限流：窗口长度由 {@code windowSeconds} 指定，窗口内计数超过 {@code limit}
 * 即短路返回 HTTP 429；{@code enabled=false} 时完全关闭限流。</p>
 *
 * @author BlueWhale
 */
@Data
@Component
@ConfigurationProperties(prefix = "whale.gateway.rate-limit")
public class RateLimitProperties {

    /** 是否启用网关限流，默认开启；关闭后所有请求直接放行 */
    private boolean enabled = true;

    /** 单个固定窗口内允许的最大请求数（阈值），默认 200 */
    private int limit = 200;

    /** 固定窗口长度（秒），默认 1；窗口编号 = 当前毫秒 / (windowSeconds * 1000) */
    private int windowSeconds = 1;

    /** Redis 限流计数键前缀，默认 whale:gateway:rate-limit */
    private String keyPrefix = "whale:gateway:rate-limit";
}
