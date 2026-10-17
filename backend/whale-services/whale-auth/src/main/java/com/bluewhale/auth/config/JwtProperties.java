package com.bluewhale.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置属性
 *
 * @author BlueWhale
 */
@Data
@Component
@ConfigurationProperties(prefix = "whale.auth.jwt")
public class JwtProperties {

    /**
     * 默认占位密钥（仅限开发环境）。
     * <p>非 dev 环境若未通过环境变量 {@code JWT_SECRET} 覆盖，将被
     * {@link SecretPlaceholderChecker} 判定为不安全的配置并拒绝启动（REQ-SEC-005）。</p>
     */
    public static final String DEFAULT_PLACEHOLDER_SECRET = "bluewhale-jwt-secret-2026-please-change-in-production";

    /** HS256 签名密钥（与 whale-gateway 保持完全一致，生产环境务必通过环境变量覆盖） */
    private String secret = DEFAULT_PLACEHOLDER_SECRET;

    /** accessToken 有效期（分钟） */
    private long expireMinutes = 120;

    /** refreshToken 有效期（分钟） */
    private long refreshExpireMinutes = 1440;
}
