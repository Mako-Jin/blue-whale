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

    /** HS256 签名密钥（与 whale-gateway 保持完全一致，生产环境务必通过环境变量覆盖） */
    private String secret = "bluewhale-jwt-secret-2026-please-change-in-production";

    /** accessToken 有效期（分钟） */
    private long expireMinutes = 120;

    /** refreshToken 有效期（分钟） */
    private long refreshExpireMinutes = 1440;
}
