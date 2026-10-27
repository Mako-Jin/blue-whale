package com.bluewhale.auth.security;

import com.bluewhale.auth.jwt.JwtTokenProvider;
import com.bluewhale.security.degradation.SecurityDegradationAlerter;
import com.bluewhale.security.token.TenantMembershipRevocationKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link RedisTenantMembershipRevocationStore} 单元测试：撤销写入、签发时间比较与 Redis 降级。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class RedisTenantMembershipRevocationStoreTest {

    private static final long TENANT_ID = 2L;
    private static final long USER_ID = 1251323419503984640L;
    private static final long ACCESS_TTL_SECONDS = 7200L;
    private static final long ISSUED_AT_SECONDS = 1_800_000_000L;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private JwtTokenProvider tokenProvider;

    @InjectMocks
    private RedisTenantMembershipRevocationStore store;

    @BeforeEach
    void resetAlarmCount() {
        SecurityDegradationAlerter.resetAlarmCount();
    }

    @Test
    @DisplayName("revoke：写入当前毫秒时间戳，TTL 覆盖 accessToken 有效期并加缓冲")
    void revokeShouldWriteEpochMilliWithTokenTtl() {
        when(tokenProvider.getAccessExpireSeconds()).thenReturn(ACCESS_TTL_SECONDS);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        long before = System.currentTimeMillis();

        store.revoke(TENANT_ID, USER_ID);

        long after = System.currentTimeMillis();
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq(TenantMembershipRevocationKeys.revocationKey(TENANT_ID, USER_ID)),
                valueCaptor.capture(), eq(ACCESS_TTL_SECONDS + 60L), eq(TimeUnit.SECONDS));
        long revokedAt = Long.parseLong(valueCaptor.getValue());
        assertTrue(revokedAt >= before && revokedAt <= after, "撤销时间应为当前毫秒时间戳");
    }

    @Test
    @DisplayName("revoke：参数缺失时跳过写入")
    void revokeShouldSkipWhenArgumentsMissing() {
        store.revoke(null, USER_ID);
        store.revoke(TENANT_ID, null);

        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("revoke：Redis 不可用时仅告警不抛异常并自增告警计数")
    void revokeShouldNotThrowWhenRedisUnavailable() {
        when(tokenProvider.getAccessExpireSeconds()).thenReturn(ACCESS_TTL_SECONDS);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new RuntimeException("connection refused"))
                .when(valueOperations).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

        assertDoesNotThrow(() -> store.revoke(TENANT_ID, USER_ID));
        assertEquals(1L, SecurityDegradationAlerter.alarmCount());
    }

    @Test
    @DisplayName("isRevoked：旧格式撤销键（Unix 秒）令牌签发时间不晚于撤销时间时返回 true")
    void isRevokedShouldReturnTrueForLegacySecondValue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(TenantMembershipRevocationKeys.revocationKey(TENANT_ID, USER_ID)))
                .thenReturn(String.valueOf(ISSUED_AT_SECONDS));

        assertTrue(store.isRevoked(TENANT_ID, USER_ID, new Date(ISSUED_AT_SECONDS * 1000L)));
    }

    @Test
    @DisplayName("isRevoked：毫秒级撤销时间早于令牌签发时间时返回 false（解绑同秒重新登录可放行）")
    void isRevokedShouldReturnFalseWhenNewTokenIssuedAfterMillisecondRevocation() {
        long revokedAtMillis = ISSUED_AT_SECONDS * 1000L + 100L;
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(String.valueOf(revokedAtMillis));

        assertFalse(store.isRevoked(TENANT_ID, USER_ID, new Date(revokedAtMillis + 1L)));
    }

    @Test
    @DisplayName("isRevoked：重新绑定后签发的新令牌返回 false")
    void isRevokedShouldReturnFalseWhenIssuedAfterRevocation() {
        long revokedAtMillis = ISSUED_AT_SECONDS * 1000L;
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(String.valueOf(revokedAtMillis));

        assertFalse(store.isRevoked(TENANT_ID, USER_ID, new Date(revokedAtMillis + 1000L)));
    }

    @Test
    @DisplayName("isRevoked：无撤销记录返回 false")
    void isRevokedShouldReturnFalseWhenKeyAbsent() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        assertFalse(store.isRevoked(TENANT_ID, USER_ID, new Date(ISSUED_AT_SECONDS * 1000L)));
    }

    @Test
    @DisplayName("isRevoked：Redis 不可用或值非法时按未撤销降级并告警")
    void isRevokedShouldReturnFalseWhenRedisUnavailable() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("connection refused"));

        assertFalse(store.isRevoked(TENANT_ID, USER_ID, new Date(ISSUED_AT_SECONDS * 1000L)));
        assertEquals(1L, SecurityDegradationAlerter.alarmCount());
    }

    @Test
    @DisplayName("isRevoked：参数缺失时返回 false，且不访问 Redis")
    void isRevokedShouldReturnFalseWhenArgumentsMissing() {
        assertFalse(store.isRevoked(null, USER_ID, new Date()));
        assertFalse(store.isRevoked(TENANT_ID, null, new Date()));
        assertFalse(store.isRevoked(TENANT_ID, USER_ID, null));

        verifyNoInteractions(redisTemplate);
    }
}
