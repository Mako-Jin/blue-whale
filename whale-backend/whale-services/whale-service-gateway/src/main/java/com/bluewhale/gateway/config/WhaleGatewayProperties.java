package com.bluewhale.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 网关配置属性
 *
 * @author BlueWhale
 */
@Data
@Component
@ConfigurationProperties(prefix = "whale.gateway")
public class WhaleGatewayProperties {

    /** JWT 配置 */
    private Jwt jwt = new Jwt();

    /** CORS 配置（P1-2） */
    private Cors cors = new Cors();

    /** 不需要认证的白名单路径（支持 Ant 通配符） */
    private List<String> whiteList = new ArrayList<>();

    /** 路由治理端点鉴权配置（P1-13） */
    private Governance governance = new Governance();

    /**
     * JWT 相关配置
     *
     * @author BlueWhale
     */
    @Data
    public static class Jwt {

        /**
         * 默认占位密钥（仅限开发环境）。
         * <p>非 dev 环境若未通过环境变量 {@code WHALE_GATEWAY_JWT_SECRET} 覆盖，将被
         * {@link GatewaySecretPlaceholderChecker} 判定为不安全的配置并拒绝启动（AUTH-01）。</p>
         */
        public static final String DEFAULT_PLACEHOLDER_SECRET = "bluewhale-jwt-secret-2026-please-change-in-production";

        /** HS256 签名密钥，必须与 whale-service-auth 签发侧一致（JWKS 模式下仅用于验签过渡期旧令牌） */
        private String secret = DEFAULT_PLACEHOLDER_SECRET;

        /** 是否启用 JWKS 验签（auth 切换 RS256 后开启）；开启后按 kid 拉取公钥，无 kid 的 HS256 令牌仍走共享密钥 */
        private boolean jwksEnabled = false;

        /** JWKS 地址（lb:// 由负载均衡 WebClient 解析） */
        private String jwksUri = "lb://whale-service-auth/oauth2/jwks";

        /** JWKS 拉取超时（毫秒）：auth 抖动时快速失败，按 HS256 回落或拒绝 RS256 令牌 */
        private long jwksTimeoutMillis = 800L;

        /** JWKS 本地缓存时长（分钟）：未知 kid 会强制刷新一次，不等待 TTL */
        private long jwksCacheMinutes = 10L;
    }

    /**
     * 跨域配置（P1-2）
     *
     * @author BlueWhale
     */
    @Data
    public static class Cors {

        /**
         * 允许的来源模式（{@code allowedOriginPatterns}，支持 {@code http://localhost:*} 通配形式）。
         * <p>默认值仅覆盖本机联调端口；生产环境应通过环境变量
         * {@code WHALE_GATEWAY_CORS_ALLOWED_ORIGINS} 覆盖为具体域名白名单。</p>
         */
        private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:*", "http://127.0.0.1:*"));
    }

    /**
     * 路由治理端点鉴权配置。
     *
     * <p>{@code /actuator/routeGovernance} 不受 JWT 过滤器保护（actuator 端点不经过路由链），
     * 且包含写操作（回滚路由），必须通过 {@code X-Governance-Token} 与本文配置的令牌比对；
     * 令牌为空时一律拒绝（fail-closed），通过环境变量
     * {@code WHALE_GATEWAY_GOVERNANCE_TOKEN} 显式启用。</p>
     *
     * @author BlueWhale
     */
    @Data
    public static class Governance {

        /** 治理端点访问令牌，空表示禁用该端点（默认 fail-closed） */
        private String token = "";
    }
}
