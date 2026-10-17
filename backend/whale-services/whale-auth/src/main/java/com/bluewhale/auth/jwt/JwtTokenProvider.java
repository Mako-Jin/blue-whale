package com.bluewhale.auth.jwt;

import com.bluewhale.auth.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * JWT Token 提供者（jjwt 0.12 API，HS256）
 * <p>与 whale-gateway 约定：</p>
 * <ul>
 *   <li>算法 HS256，secret = whale.auth.jwt.secret</li>
 *   <li>claims：sub=username、userId、roles（逗号分隔角色 code）、tokenType（access/refresh）、jti</li>
 *   <li>Header：Authorization: Bearer &lt;token&gt;</li>
 * </ul>
 * <p>{@code jti} 为每枚令牌唯一的随机标识（标准 JWT ID claim），便于登出黑名单按令牌粒度
 * 追踪与审计（REQ-SEC-004）；access 与 refresh 令牌均携带。</p>
 *
 * @author BlueWhale
 */
@Component
public class JwtTokenProvider {

    public static final String CLAIM_USER_ID = "userId";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_TOKEN_TYPE = "tokenType";
    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    /** Authorization 头凭据前缀，签发与校验两侧共用，避免各自定义 */
    public static final String BEARER_PREFIX = "Bearer ";

    /** 毫秒换算秒的进制 */
    private static final long MILLIS_PER_SECOND = 1000L;

    private final JwtProperties properties;

    private final SecretKey secretKey;

    @Getter
    private final long accessExpireSeconds;

    /**
     * 构造器：依据配置中的密钥派生 HMAC 密钥，并换算 accessToken 有效期
     *
     * @param properties JWT 配置（密钥、有效期等）
     */
    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        this.secretKey = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
        this.accessExpireSeconds = properties.getExpireMinutes() * 60;
    }

    /**
     * 生成 accessToken：sub=username、userId、roles（逗号分隔角色 code），带唯一 jti
     *
     * @param userId 用户 ID
     * @param username 用户名（作为 sub）
     * @param roles 角色 code 列表（逗号分隔写入 roles claim）
     * @return 签名后的 accessToken 字符串
     */
    public String generateAccessToken(Long userId, String username, List<String> roles) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + properties.getExpireMinutes() * 60_000);
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(username)
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_ROLES, String.join(",", roles))
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 生成 refreshToken，带唯一 jti
     *
     * @param userId 用户 ID
     * @param username 用户名（作为 sub）
     * @return 签名后的 refreshToken 字符串
     */
    public String generateRefreshToken(Long userId, String username) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + properties.getRefreshExpireMinutes() * 60_000);
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(username)
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_REFRESH)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 解析并校验 token，无效 / 过期 / 签名不符返回 null（不抛异常）
     *
     * @param token 待解析的 token
     * @return 令牌载荷 claims；无效时返回 {@code null}
     */
    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 计算 token 剩余有效期（秒）。
     * 供登出黑名单确定 Redis 键 TTL 使用：无法解析或已过期一律返回 0。
     *
     * @param token 待计算的 token
     * @return 剩余有效秒数，非正数表示已失效
     */
    public long getRemainingValiditySeconds(String token) {
        Claims claims = parse(token);
        if (claims == null || claims.getExpiration() == null) {
            return 0L;
        }
        long remainingMillis = claims.getExpiration().getTime() - System.currentTimeMillis();
        return remainingMillis > 0L ? remainingMillis / MILLIS_PER_SECOND : 0L;
    }
}
