package com.bluewhale.auth.security;

import com.bluewhale.auth.jwt.JwtTokenProvider;
import com.bluewhale.security.degradation.SecurityDegradationAlerter;
import com.bluewhale.security.token.UserAccessRevocationKeys;
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
 * {@link RedisUserAccessRevocationStore} 单元测试：跨租户撤销写入、签发时间比较与 Redis 降级。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class RedisUserAccessRevocationStoreTest {

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
    private RedisUserAccessRevocationStore store;

    @BeforeEach
    void resetAlarmCount() {
        SecurityDegradationAlerter.resetAlarmCount();
    }

    @Test
    @DisplayName("revoke：写入当前 Unix 秒与覆盖 accessToken 有效期的 TTL，键不带租户段")
    void revokeShouldWriteEpochSecondWithTokenTtl() {
        when(tokenProvider.getAccessExpireSeconds()).thenReturn(ACCESS_TTL_SECONDS);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        long before = System.currentTimeMillis() / 1000L;

        store.revoke(USER_ID);

        long after = System.currentTimeMillis() / 1000L;
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq(UserAccessRevocationKeys.revocationKey(USER_ID)),
                valueCaptor.capture(), eq(ACCESS_TTL_SECONDS + 60L), eq(TimeUnit.SECONDS));
        long revokedAt = Long.parseLong(valueCaptor.getValue());
        assertTrue(revokedAt >= before && revokedAt <= after, "撤销时间应为当前 Unix 秒");
    }

    @Test
    @DisplayName("revoke：用户 ID 缺失时跳过写入")
    void revokeShouldSkipWhenUserIdMissing() {
        store.revoke(null);

        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("revoke：Redis 不可用时仅告警不抛异常并自增告警计数")
    void revokeShouldNotThrowWhenRedisUnavailable() {
        when(tokenProvider.getAccessExpireSeconds()).thenReturn(ACCESS_TTL_SECONDS);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new RuntimeException("connection refused"))
                .when(valueOperations).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

        assertDoesNotThrow(() -> store.revoke(USER_ID));
        assertEquals(1L, SecurityDegradationAlerter.alarmCount());
    }

    @Test
    @DisplayName("isRevoked：令牌签发时间不晚于撤销时间时返回 true")
    void isRevokedShouldReturnTrueWhenIssuedBeforeRevocation() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(UserAccessRevocationKeys.revocationKey(USER_ID)))
                .thenReturn(String.valueOf(ISSUED_AT_SECONDS));

        assertTrue(store.isRevoked(USER_ID, new Date(ISSUED_AT_SECONDS * 1000L)));
    }

    @Test
    @DisplayName("isRevoked：重新启用后签发的新令牌返回 false")
    void isRevokedShouldReturnFalseWhenIssuedAfterRevocation() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(String.valueOf(ISSUED_AT_SECONDS));

        assertFalse(store.isRevoked(USER_ID, new Date((ISSUED_AT_SECONDS + 1L) * 1000L)));
    }

    @Test
    @DisplayName("isRevoked：无撤销记录返回 false")
    void isRevokedShouldReturnFalseWhenKeyAbsent() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        assertFalse(store.isRevoked(USER_ID, new Date(ISSUED_AT_SECONDS * 1000L)));
    }

    @Test
    @DisplayName("isRevoked：Redis 不可用或值非法时按未撤销降级并告警")
    void isRevokedShouldReturnFalseWhenRedisUnavailable() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("connection refused"));

        assertFalse(store.isRevoked(USER_ID, new Date(ISSUED_AT_SECONDS * 1000L)));
        assertEquals(1L, SecurityDegradationAlerter.alarmCount());
    }

    @Test
    @DisplayName("isRevoked：参数缺失时返回 false，且不访问 Redis")
    void isRevokedShouldReturnFalseWhenArgumentsMissing() {
        assertFalse(store.isRevoked(null, new Date()));
        assertFalse(store.isRevoked(USER_ID, null));

        verifyNoInteractions(redisTemplate);
    }
}
