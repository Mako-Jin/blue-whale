package com.bluewhale.gateway.security;

import com.bluewhale.security.degradation.SecurityDegradationAlerter;
import com.bluewhale.security.token.TenantStatusKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link TenantStatusChecker} 单元测试：停用、到期、长期有效、缓存缺失与 Redis 降级。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class TenantStatusCheckerTest {

    private static final long TENANT_ID = 2L;
    private static final long ONE_HOUR_SECONDS = 3600L;

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    @InjectMocks
    private TenantStatusChecker checker;

    @BeforeEach
    void resetAlarmCount() {
        SecurityDegradationAlerter.resetAlarmCount();
    }

    private static long nowSeconds() {
        return System.currentTimeMillis() / 1000L;
    }

    @Test
    @DisplayName("isUnavailable：状态为停用返回 true，且键为状态+到期两个约定键")
    void shouldReturnTrueWhenTenantDisabled() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.multiGet(anyList()))
                .thenReturn(Mono.just(Arrays.asList(TenantStatusKeys.DISABLED, null)));

        assertTrue(checker.isUnavailable(String.valueOf(TENANT_ID)).block());

        verify(valueOperations).multiGet(List.of(TenantStatusKeys.statusKey(TENANT_ID),
                TenantStatusKeys.expireKey(TENANT_ID)));
    }

    @Test
    @DisplayName("isUnavailable：启用但已到达到期秒数返回 true")
    void shouldReturnTrueWhenTenantExpired() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.multiGet(anyList()))
                .thenReturn(Mono.just(Arrays.asList(TenantStatusKeys.ENABLED,
                        String.valueOf(nowSeconds() - ONE_HOUR_SECONDS))));

        assertTrue(checker.isUnavailable(String.valueOf(TENANT_ID)).block());
    }

    @Test
    @DisplayName("isUnavailable：启用且未到期返回 false")
    void shouldReturnFalseWhenEnabledAndNotExpired() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.multiGet(anyList()))
                .thenReturn(Mono.just(Arrays.asList(TenantStatusKeys.ENABLED,
                        String.valueOf(nowSeconds() + ONE_HOUR_SECONDS))));

        assertFalse(checker.isUnavailable(String.valueOf(TENANT_ID)).block());
    }

    @Test
    @DisplayName("isUnavailable：长期有效（到期键缺失）返回 false")
    void shouldReturnFalseWhenExpireKeyAbsent() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.multiGet(anyList()))
                .thenReturn(Mono.just(Arrays.asList(TenantStatusKeys.ENABLED, null)));

        assertFalse(checker.isUnavailable(String.valueOf(TENANT_ID)).block());
    }

    @Test
    @DisplayName("isUnavailable：状态与到期缓存都缺失时降级放行")
    void shouldReturnFalseWhenCachesMissing() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.multiGet(anyList()))
                .thenReturn(Mono.just(Arrays.asList(null, null)));

        assertFalse(checker.isUnavailable(String.valueOf(TENANT_ID)).block());
    }

    @Test
    @DisplayName("isUnavailable：到期时间值非法时按未到期放行")
    void shouldReturnFalseWhenExpireValueInvalid() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.multiGet(anyList()))
                .thenReturn(Mono.just(Arrays.asList(TenantStatusKeys.ENABLED, "not-a-number")));

        assertFalse(checker.isUnavailable(String.valueOf(TENANT_ID)).block());
    }

    @Test
    @DisplayName("isUnavailable：Redis 不可用时按可用降级放行并自增告警计数")
    void shouldReturnFalseAndRaiseAlarmWhenRedisUnavailable() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.multiGet(anyList()))
                .thenReturn(Mono.error(new RuntimeException("connection refused")));

        assertFalse(checker.isUnavailable(String.valueOf(TENANT_ID)).block());
        assertEquals(1L, SecurityDegradationAlerter.alarmCount());
    }

    @Test
    @DisplayName("isUnavailable：租户 ID 非法时返回 false，且不访问 Redis")
    void shouldReturnFalseWhenTenantIdInvalid() {
        assertFalse(checker.isUnavailable(null).block());
        assertFalse(checker.isUnavailable("bad").block());

        verifyNoInteractions(redisTemplate);
    }
}
