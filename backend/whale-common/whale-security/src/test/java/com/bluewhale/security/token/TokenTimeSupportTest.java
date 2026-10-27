package com.bluewhale.security.token;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TokenTimeSupport} 单元测试：新毫秒 claim、旧令牌回退、新旧撤销值兼容。
 *
 * @author BlueWhale
 */
class TokenTimeSupportTest {

    private static final long ISSUED_AT_SECONDS = 1_800_000_000L;

    @Test
    @DisplayName("issuedAtMillis：优先 iatMs，缺失时回退 iat 秒级时间")
    void shouldPreferIssuedAtMillisClaim() {
        Claims withMillis = Jwts.claims()
                .issuedAt(new Date(ISSUED_AT_SECONDS * 1000L))
                .add(TokenTimeSupport.CLAIM_ISSUED_AT_MILLIS, ISSUED_AT_SECONDS * 1000L + 321L)
                .build();
        Claims legacy = Jwts.claims()
                .issuedAt(new Date(ISSUED_AT_SECONDS * 1000L))
                .build();

        assertEquals(ISSUED_AT_SECONDS * 1000L + 321L, TokenTimeSupport.issuedAtMillis(withMillis));
        assertEquals(ISSUED_AT_SECONDS * 1000L, TokenTimeSupport.issuedAtMillis(legacy));
        assertEquals(0L, TokenTimeSupport.issuedAtMillis((Claims) null));
    }

    @Test
    @DisplayName("isRevoked：毫秒级撤销值按精确时间比较，同秒新令牌放行")
    void shouldCompareMillisecondRevocationPrecisely() {
        long revokedAtMillis = ISSUED_AT_SECONDS * 1000L + 100L;

        assertTrue(TokenTimeSupport.isRevoked(revokedAtMillis, String.valueOf(revokedAtMillis)));
        assertTrue(TokenTimeSupport.isRevoked(revokedAtMillis - 1L, String.valueOf(revokedAtMillis)));
        assertFalse(TokenTimeSupport.isRevoked(revokedAtMillis + 1L, String.valueOf(revokedAtMillis)));
    }

    @Test
    @DisplayName("isRevoked：旧秒级撤销值按秒粒度比较，兼容滚动升级")
    void shouldKeepSecondGranularityForLegacyValue() {
        long issuedAtMillis = ISSUED_AT_SECONDS * 1000L + 999L;

        assertTrue(TokenTimeSupport.isRevoked(issuedAtMillis, String.valueOf(ISSUED_AT_SECONDS)));
        assertFalse(TokenTimeSupport.isRevoked((ISSUED_AT_SECONDS + 1L) * 1000L,
                String.valueOf(ISSUED_AT_SECONDS)));
    }

    @Test
    @DisplayName("isRevoked：撤销值为空或非法签发时间返回 false")
    void shouldReturnFalseWhenInputMissing() {
        assertFalse(TokenTimeSupport.isRevoked(ISSUED_AT_SECONDS * 1000L, null));
        assertFalse(TokenTimeSupport.isRevoked(ISSUED_AT_SECONDS * 1000L, " "));
        assertFalse(TokenTimeSupport.isRevoked(0L, String.valueOf(ISSUED_AT_SECONDS)));
    }
}
