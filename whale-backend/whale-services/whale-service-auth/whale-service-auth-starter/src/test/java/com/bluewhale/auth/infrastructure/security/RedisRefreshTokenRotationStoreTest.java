package com.bluewhale.auth.infrastructure.security;

import com.bluewhale.security.degradation.SecurityDegradationAlerter;
import com.bluewhale.security.token.TokenBlacklistKeys;
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

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link RedisRefreshTokenRotationStore} 单元测试（AUTH-39 / A3）：
 * 首次消费抢占成功、二次消费失败、family 吊销可查、Redis 异常 fail-closed 与降级告警计数。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class RedisRefreshTokenRotationStoreTest {

    private static final String JTI = "jti-0001";
    private static final String FAMILY_ID = "family-0001";
    private static final String PLACEHOLDER_VALUE = "1";
    private static final long VALID_TTL_SECONDS = 600L;
    private static final long FAMILY_TTL_SECONDS = 86400L;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private RedisRefreshTokenRotationStore store;

    @BeforeEach
    void resetAlarmCount() {
        SecurityDegradationAlerter.resetAlarmCount();
    }

    @Test
    @DisplayName("markUsed：SET NX 抢占成功（首次消费）返回 true，键与 TTL 符合约定")
    void markUsedShouldReturnTrueOnFirstConsume() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(Boolean.TRUE);

        assertTrue(store.markUsed(JTI, VALID_TTL_SECONDS));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).setIfAbsent(keyCaptor.capture(), eq(PLACEHOLDER_VALUE),
                eq(VALID_TTL_SECONDS), eq(TimeUnit.SECONDS));
        assertEquals(TokenBlacklistKeys.refreshUsedKey(JTI), keyCaptor.getValue());
    }

    @Test
    @DisplayName("markUsed：键已存在（已被消费）时返回 false")
    void markUsedShouldReturnFalseWhenAlreadyConsumed() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(Boolean.FALSE);

        assertFalse(store.markUsed(JTI, VALID_TTL_SECONDS));
    }

    @Test
    @DisplayName("markUsed：返回值缺失（SET NX 未写入）时按未抢占处理返回 false")
    void markUsedShouldReturnFalseWhenResultNull() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(null);

        assertFalse(store.markUsed(JTI, VALID_TTL_SECONDS));
    }

    @Test
    @DisplayName("markUsed：TTL 非正（已过期令牌）时跳过写入并返回 false")
    void markUsedShouldSkipWhenTtlNonPositive() {
        assertFalse(store.markUsed(JTI, 0L));

        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("markUsed：Redis 异常时 fail-closed 返回 false 并触发降级告警")
    void markUsedShouldFailClosedWhenRedisUnavailable() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new RuntimeException("connection refused"))
                .when(valueOperations).setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS));

        assertFalse(store.markUsed(JTI, VALID_TTL_SECONDS));
        assertEquals(1L, SecurityDegradationAlerter.alarmCount());
    }

    @Test
    @DisplayName("isFamilyRevoked：键存在返回 true，不存在返回 false")
    void isFamilyRevokedShouldReflectKeyPresence() {
        when(redisTemplate.hasKey(TokenBlacklistKeys.refreshFamilyKey(FAMILY_ID))).thenReturn(Boolean.TRUE);
        assertTrue(store.isFamilyRevoked(FAMILY_ID));

        when(redisTemplate.hasKey(TokenBlacklistKeys.refreshFamilyKey(FAMILY_ID))).thenReturn(Boolean.FALSE);
        assertFalse(store.isFamilyRevoked(FAMILY_ID));
    }

    @Test
    @DisplayName("isFamilyRevoked：Redis 异常时 fail-closed 返回 true 并触发降级告警")
    void isFamilyRevokedShouldFailClosedWhenRedisUnavailable() {
        when(redisTemplate.hasKey(anyString())).thenThrow(new RuntimeException("connection refused"));

        assertTrue(store.isFamilyRevoked(FAMILY_ID));
        assertEquals(1L, SecurityDegradationAlerter.alarmCount());
    }

    @Test
    @DisplayName("revokeFamily：写入 family 吊销键，TTL 取最长 refresh 有效期")
    void revokeFamilyShouldWriteFamilyKey() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        store.revokeFamily(FAMILY_ID, FAMILY_TTL_SECONDS);

        verify(valueOperations).set(TokenBlacklistKeys.refreshFamilyKey(FAMILY_ID), PLACEHOLDER_VALUE,
                FAMILY_TTL_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("revokeFamily：Redis 异常时仅告警不抛异常，并触发降级告警")
    void revokeFamilyShouldNotThrowWhenRedisUnavailable() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new RuntimeException("connection refused"))
                .when(valueOperations).set(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS));

        store.revokeFamily(FAMILY_ID, FAMILY_TTL_SECONDS);

        assertEquals(1L, SecurityDegradationAlerter.alarmCount());
    }
}
