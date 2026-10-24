package com.bluewhale.security.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link JwtUtils} 单元测试
 *
 * @author BlueWhale
 */
class JwtUtilsTest {

    /** 合法密钥（36 字节，满足 HS256 最低要求） */
    private static final String SECRET = "bluewhale-test-secret-key-0123456789";

    /** 另一合法密钥（用于验签失败场景） */
    private static final String OTHER_SECRET = "bluewhale-other-secret-key-9876543210";

    /** 过短密钥 */
    private static final String SHORT_SECRET = "short-secret";

    /** 令牌有效期（毫秒） */
    private static final long TTL_MILLIS = 60_000L;

    /**
     * 生成后可解析出 subject 与自定义载荷
     */
    @Test
    @DisplayName("生成与解析：subject 与载荷完整保留")
    void shouldGenerateAndParseToken() {
        String token = JwtUtils.generateToken("user-1", Map.of("role", "admin"), SECRET, TTL_MILLIS);
        var claims = JwtUtils.parseToken(token, SECRET);
        assertEquals("user-1", claims.getSubject());
        assertEquals("admin", claims.get("role"));
        assertTrue(claims.getExpiration().getTime() > System.currentTimeMillis());
        assertTrue(JwtUtils.validateToken(token, SECRET));
    }

    /**
     * 不带 subject 的生成入口
     */
    @Test
    @DisplayName("简化入口：仅载荷也能生成与校验")
    void shouldGenerateTokenWithoutSubject() {
        String token = JwtUtils.generateToken(Map.of("tenant", "blue"), SECRET, TTL_MILLIS);
        assertTrue(JwtUtils.validateToken(token, SECRET));
        assertEquals("blue", JwtUtils.parseToken(token, SECRET).get("tenant"));
    }

    /**
     * 换密钥后验签失败
     */
    @Test
    @DisplayName("校验：错误密钥返回 false")
    void shouldRejectWrongSecret() {
        String token = JwtUtils.generateToken(Map.of("k", "v"), SECRET, TTL_MILLIS);
        assertFalse(JwtUtils.validateToken(token, OTHER_SECRET));
    }

    /**
     * 篡改令牌后校验失败
     */
    @Test
    @DisplayName("校验：篡改令牌返回 false")
    void shouldRejectTamperedToken() {
        String token = JwtUtils.generateToken(Map.of("k", "v"), SECRET, TTL_MILLIS);
        String tampered = token.substring(0, token.length() - 1)
                + (token.endsWith("a") ? "b" : "a");
        assertFalse(JwtUtils.validateToken(tampered, SECRET));
    }

    /**
     * 过期令牌校验失败
     */
    @Test
    @DisplayName("校验：过期令牌返回 false")
    void shouldRejectExpiredToken() throws InterruptedException {
        String token = JwtUtils.generateToken(Map.of("k", "v"), SECRET, 1L);
        Thread.sleep(10L);
        assertFalse(JwtUtils.validateToken(token, SECRET));
    }

    /**
     * 弱密钥直接拒绝
     */
    @Test
    @DisplayName("生成：密钥不足 32 字节抛出 IllegalArgumentException")
    void shouldRejectWeakSecret() {
        assertThrows(IllegalArgumentException.class,
                () -> JwtUtils.generateToken(Map.of("k", "v"), SHORT_SECRET, TTL_MILLIS));
        String token = JwtUtils.generateToken(Map.of("k", "v"), SECRET, TTL_MILLIS);
        assertFalse(JwtUtils.validateToken(token, SHORT_SECRET));
    }

    /**
     * 空令牌被拒绝
     */
    @Test
    @DisplayName("解析：空令牌抛出参数异常")
    void shouldRejectBlankToken() {
        assertThrows(IllegalArgumentException.class, () -> JwtUtils.parseToken(" ", SECRET));
    }

    /**
     * 续签保留载荷并延长有效期
     */
    @Test
    @DisplayName("续签：保留 subject/载荷并刷新过期时间")
    void shouldRefreshToken() {
        String token = JwtUtils.generateToken("user-2", Map.of("role", "viewer"), SECRET, TTL_MILLIS);
        String refreshed = JwtUtils.refreshToken(token, SECRET, TTL_MILLIS * 2);
        var claims = JwtUtils.parseToken(refreshed, SECRET);
        assertEquals("user-2", claims.getSubject());
        assertEquals("viewer", claims.get("role"));
        assertTrue(claims.getExpiration().getTime() > System.currentTimeMillis() + TTL_MILLIS);
    }
}
