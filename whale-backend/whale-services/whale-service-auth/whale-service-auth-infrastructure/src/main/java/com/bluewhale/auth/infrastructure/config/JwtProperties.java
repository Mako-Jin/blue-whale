package com.bluewhale.auth.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

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

    /** HS256 签名密钥（与 whale-service-gateway 保持完全一致，生产环境务必通过环境变量覆盖） */
    private String secret = DEFAULT_PLACEHOLDER_SECRET;

    /**
     * RSA 私钥（PKCS#8，PEM 或 Base64 DER）。
     *
     * <p>配置后启用 RS256：新令牌以该私钥签名并在 JWT 头写入 {@link #keyId}，
     * {@code /oauth2/jwks} 仅发布对应公钥，从而实现「新旧密钥并行、无需全量重登」的轮换。
     * 留空则保持 HS256（兼容存量部署），此时 JWKS 端点会被关闭，避免对称密钥外泄。</p>
     */
    private String privateKey = "";

    /** 默认签名密钥标识（仅在未配置 key-id 时使用） */
    public static final String DEFAULT_KEY_ID = "whale-1";

    /** 当前签名密钥标识（写入 JWT 头 kid 与 JWKS），轮换时变更该值区分新旧密钥 */
    private String keyId = DEFAULT_KEY_ID;

    /**
     * 上一代公钥（kid → PKCS#8/X.509 PEM 或 Base64 DER）。
     *
     * <p>轮换时把旧公钥按<b>原 kid</b> 放入此映射，并至少保留一个 accessToken 有效期：
     * 新令牌用新私钥签名，存量令牌仍可按其 kid 用旧公钥验签，实现平滑过渡。</p>
     */
    private Map<String, String> previousPublicKeys = new LinkedHashMap<>();

    /** accessToken 有效期（分钟） */
    private long expireMinutes = 120;

    /** refreshToken 有效期（分钟） */
    private long refreshExpireMinutes = 1440;
}
