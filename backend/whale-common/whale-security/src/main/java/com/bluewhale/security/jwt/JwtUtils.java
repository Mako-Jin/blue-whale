package com.bluewhale.security.jwt;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * JWT 工具类
 * <p>基于 jjwt 0.12.x 的 HS256 对称签名实现，提供 Token 生成、解析、校验与续签。</p>
 * <p><b>约束</b>：密钥 UTF-8 字节长度必须 ≥32（HS256 最低要求），弱密钥直接拒绝，
 * 避免误用默认口令导致签名可被穷举。</p>
 *
 * @author BlueWhale
 */
public final class JwtUtils {

    /** HS256 密钥最小字节长度 */
    public static final int MIN_SECRET_BYTES = 32;

    /**
     * 私有构造器，禁止实例化
     */
    private JwtUtils() {
    }

    /**
     * 生成 Token（不带 subject）
     *
     * @param claims           自定义载荷
     * @param secret           签名密钥
     * @param expirationMillis 有效期（毫秒）
     * @return 紧凑格式 Token
     */
    public static String generateToken(Map<String, Object> claims, String secret, long expirationMillis) {
        return generateToken(null, claims, secret, expirationMillis);
    }

    /**
     * 生成 Token
     *
     * @param subject          主题（一般为用户标识），可为 null
     * @param claims           自定义载荷，可为 null
     * @param secret           签名密钥
     * @param expirationMillis 有效期（毫秒，必须为正数）
     * @return 紧凑格式 Token
     */
    public static String generateToken(String subject, Map<String, Object> claims, String secret,
                                       long expirationMillis) {
        if (expirationMillis <= 0) {
            throw new IllegalArgumentException("有效期必须为正数");
        }
        long now = System.currentTimeMillis();
        var builder = Jwts.builder();
        if (subject != null) {
            builder.subject(subject);
        }
        if (claims != null && !claims.isEmpty()) {
            builder.claims(claims);
        }
        return builder.issuedAt(new Date(now))
                .expiration(new Date(now + expirationMillis))
                .signWith(resolveKey(secret))
                .compact();
    }

    /**
     * 解析并验签 Token
     *
     * @param token  Token
     * @param secret 签名密钥
     * @return 载荷
     * @throws io.jsonwebtoken.JwtException 签名不匹配、过期或结构非法
     */
    public static Claims parseToken(String token, String secret) {
        return Jwts.parser()
                .verifyWith(resolveKey(secret))
                .build()
                .parseSignedClaims(requireToken(token))
                .getPayload();
    }

    /**
     * 校验 Token 是否有效（验签且未过期）
     *
     * @param token  Token
     * @param secret 签名密钥
     * @return 有效返回 true，任意异常返回 false
     */
    public static boolean validateToken(String token, String secret) {
        try {
            parseToken(token, secret);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    /**
     * 续签 Token：沿用原载荷与 subject，重设签发/过期时间
     *
     * @param token            原 Token（必须有效）
     * @param secret           签名密钥
     * @param expirationMillis 新有效期（毫秒，必须为正数）
     * @return 新 Token
     */
    public static String refreshToken(String token, String secret, long expirationMillis) {
        Claims claims = parseToken(token, secret);
        Map<String, Object> payload = new HashMap<>(claims);
        payload.remove(Claims.ISSUED_AT);
        payload.remove(Claims.EXPIRATION);
        payload.remove(Claims.NOT_BEFORE);
        return generateToken(claims.getSubject(), payload, secret, expirationMillis);
    }

    /**
     * 将字符串密钥转换为 HMAC 密钥
     *
     * @param secret 密钥
     * @return HMAC-SHA 密钥
     */
    private static SecretKey resolveKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("JWT 密钥不能为空");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException("JWT 密钥长度不足，至少 " + MIN_SECRET_BYTES + " 字节");
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    /**
     * 校验 Token 文本非空白
     *
     * @param token Token
     * @return 原 Token
     */
    private static String requireToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token 不能为空");
        }
        return token;
    }
}
