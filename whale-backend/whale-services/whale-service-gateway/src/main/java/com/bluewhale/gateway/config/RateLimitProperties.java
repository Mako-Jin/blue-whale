package com.bluewhale.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 网关入口限流配置属性（T5.4 / AUTH-30）。
 * <p>基于 Redis 计数器的固定窗口限流：窗口长度由 {@code windowSeconds} 指定，窗口内计数超过 {@code limit}
 * 即短路返回 HTTP 429；{@code enabled=false} 时完全关闭限流。</p>
 * <p>{@code path-rules} 为按路径特化的更严规则（如前端/行为上报接口），
 * 命中后以规则自身阈值替代全局阈值；未命中任何规则的路径沿用全局 {@code limit} / {@code windowSeconds}。</p>
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

    /**
     * 是否信任 {@code X-Forwarded-For} 首段作为客户端标识，默认 {@code true}。
     *
     * <p>网关部署在 Nginx 之后，由 Nginx 覆写该头，取首段即真实客户端 IP。
     * 若网关被直接暴露（前面没有可信代理），客户端可伪造该头绕过限流，
     * 此时应置为 {@code false}，改用 TCP 连接的远端地址。</p>
     */
    private boolean trustForwardedFor = true;

    /** 按路径特化的限流规则（默认空列表，此时行为与仅全局规则完全一致） */
    private List<PathRule> pathRules = new ArrayList<>();

    /**
     * 按路径限流规则：{@code pathPattern} 命中请求路径时，以本规则的 {@code limit} / {@code windowSeconds}
     * 替代全局阈值；多条规则命中时取最长 {@code pathPattern}（特化优先）。
     *
     * @author BlueWhale
     */
    @Data
    public static class PathRule {

        /** Ant 风格路径模式，如 {@code /audit/v1/log/frontend/**} */
        private String pathPattern;

        /** 本规则单个窗口内允许的最大请求数 */
        private int limit;

        /** 本规则固定窗口长度（秒） */
        private int windowSeconds;
    }
}
