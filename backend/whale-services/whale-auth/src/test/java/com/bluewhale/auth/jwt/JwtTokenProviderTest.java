package com.bluewhale.auth.jwt;

import com.bluewhale.auth.config.JwtProperties;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link JwtTokenProvider} 单元测试：令牌签发、解析、剩余有效期计算（REQ-QA-002 / T5.19）。
 *
 * @author BlueWhale
 */
class JwtTokenProviderTest {

    /** 测试用 HS256 密钥（长度须 ≥ 32 字节，满足 256 bit） */
    private static final String SECRET = "unit-test-secret-key-with-enough-length-0123456789";

    /** 另一把密钥，用于验证签名不匹配场景 */
    private static final String OTHER_SECRET = "another-unit-test-secret-key-9876543210-abcdefgh";

    private static final String USERNAME = "alice";
    private static final long USER_ID = 1251323419503984640L;
    private static final List<String> ROLES = List.of("admin", "user");
    private static final long ACCESS_EXPIRE_MINUTES = 120L;
    private static final long REFRESH_EXPIRE_MINUTES = 1440L;
    private static final long SECONDS_PER_MINUTE = 60L;

    private static JwtTokenProvider provider(String secret, long expireMinutes, long refreshExpireMinutes) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(secret);
        properties.setExpireMinutes(expireMinutes);
        properties.setRefreshExpireMinutes(refreshExpireMinutes);
        return new JwtTokenProvider(properties);
    }

    private static JwtTokenProvider provider(long expireMinutes, long refreshExpireMinutes) {
        return provider(SECRET, expireMinutes, refreshExpireMinutes);
    }

    @Test
    @DisplayName("generateAccessToken：claims 含 sub/userId/roles/tokenType=access 且可被自身解析")
    void shouldGenerateParsableAccessToken() {
        JwtTokenProvider tokenProvider = provider(ACCESS_EXPIRE_MINUTES, REFRESH_EXPIRE_MINUTES);

        String token = tokenProvider.generateAccessToken(USER_ID, USERNAME, ROLES);
        Claims claims = tokenProvider.parse(token);

        assertNotNull(claims);
        assertEquals(USERNAME, claims.getSubject());
        assertEquals(USER_ID, ((Number) claims.get(JwtTokenProvider.CLAIM_USER_ID)).longValue());
        assertEquals("admin,user", claims.get(JwtTokenProvider.CLAIM_ROLES));
        assertEquals(JwtTokenProvider.TOKEN_TYPE_ACCESS,
                claims.get(JwtTokenProvider.CLAIM_TOKEN_TYPE));
    }

    @Test
    @DisplayName("generateRefreshToken：tokenType=refresh，且不含 roles")
    void shouldGenerateRefreshToken() {
        JwtTokenProvider tokenProvider = provider(ACCESS_EXPIRE_MINUTES, REFRESH_EXPIRE_MINUTES);

        String token = tokenProvider.generateRefreshToken(USER_ID, USERNAME);
        Claims claims = tokenProvider.parse(token);

        assertNotNull(claims);
        assertEquals(USERNAME, claims.getSubject());
        assertEquals(USER_ID, ((Number) claims.get(JwtTokenProvider.CLAIM_USER_ID)).longValue());
        assertEquals(JwtTokenProvider.TOKEN_TYPE_REFRESH,
                claims.get(JwtTokenProvider.CLAIM_TOKEN_TYPE));
        assertNull(claims.get(JwtTokenProvider.CLAIM_ROLES));
    }

    @Test
    @DisplayName("parse：签名不匹配的 token 返回 null（密钥参与校验，不信任 token 头 alg）")
    void parseShouldReturnNullWhenSignatureMismatch() {
        String token = provider(ACCESS_EXPIRE_MINUTES, REFRESH_EXPIRE_MINUTES)
                .generateAccessToken(USER_ID, USERNAME, ROLES);

        JwtTokenProvider otherProvider = provider(OTHER_SECRET, ACCESS_EXPIRE_MINUTES, REFRESH_EXPIRE_MINUTES);

        assertNull(otherProvider.parse(token));
    }

    @Test
    @DisplayName("getRemainingValiditySeconds：有效 token 返回正数且不超过签发时长")
    void remainingValidityShouldBePositiveForValidToken() {
        JwtTokenProvider tokenProvider = provider(ACCESS_EXPIRE_MINUTES, REFRESH_EXPIRE_MINUTES);

        String token = tokenProvider.generateAccessToken(USER_ID, USERNAME, ROLES);
        long remaining = tokenProvider.getRemainingValiditySeconds(token);

        assertTrue(remaining > 0L, "有效 token 的剩余有效期应为正数");
        assertTrue(remaining <= ACCESS_EXPIRE_MINUTES * SECONDS_PER_MINUTE,
                "剩余有效期不应超过签发时长");
    }

    @Test
    @DisplayName("getRemainingValiditySeconds：已过期 token 返回 0")
    void remainingValidityShouldBeZeroForExpiredToken() {
        JwtTokenProvider expiredProvider = provider(-1L, REFRESH_EXPIRE_MINUTES);

        String token = expiredProvider.generateAccessToken(USER_ID, USERNAME, ROLES);

        assertEquals(0L, expiredProvider.getRemainingValiditySeconds(token));
    }

    @Test
    @DisplayName("getRemainingValiditySeconds：不可解析 token 返回 0")
    void remainingValidityShouldBeZeroForMalformedToken() {
        JwtTokenProvider tokenProvider = provider(ACCESS_EXPIRE_MINUTES, REFRESH_EXPIRE_MINUTES);

        assertEquals(0L, tokenProvider.getRemainingValiditySeconds("not-a-valid-jwt"));
    }
}
