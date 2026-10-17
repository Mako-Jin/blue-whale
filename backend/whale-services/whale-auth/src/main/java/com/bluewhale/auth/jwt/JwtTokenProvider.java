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

/**
 * JWT Token 提供者（jjwt 0.12 API，HS256）
 * <p>与 whale-gateway 约定：</p>
 * <ul>
 *   <li>算法 HS256，secret = whale.auth.jwt.secret</li>
 *   <li>claims：sub=username、userId、roles（逗号分隔角色 code）、tokenType（access/refresh）</li>
 *   <li>Header：Authorization: Bearer &lt;token&gt;</li>
 * </ul>
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

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        this.secretKey = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
        this.accessExpireSeconds = properties.getExpireMinutes() * 60;
    }

    /**
     * 生成 accessToken：sub=username、userId、roles（逗号分隔角色 code）
     */
    public String generateAccessToken(Long userId, String username, List<String> roles) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + properties.getExpireMinutes() * 60_000);
        return Jwts.builder()
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
     * 生成 refreshToken
     */
    public String generateRefreshToken(Long userId, String username) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + properties.getRefreshExpireMinutes() * 60_000);
        return Jwts.builder()
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
