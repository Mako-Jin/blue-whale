package com.bluewhale.auth.infrastructure.security;

import com.bluewhale.security.degradation.SecurityDegradationAlerter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RedisLoginAttemptStore} 单元测试（Wave 6.27）
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("登录失败计数存储")
class RedisLoginAttemptStoreTest {

    /** 测试用租户 */
    private static final Long TENANT_ID = 1L;

    /** 测试用用户名（故意大写，验证键统一小写） */
    private static final String USERNAME = "Alice";

    /** 期望的 Redis 键 */
    private static final String KEY = "whale:auth:login-fail:1:alice";

    /** 测试用失败上限 */
    private static final int MAX_ATTEMPTS = 5;

    /** 测试用窗口（秒） */
    private static final long WINDOW_SECONDS = 900L;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisLoginAttemptStore newStore() {
        return new RedisLoginAttemptStore(redisTemplate, MAX_ATTEMPTS, WINDOW_SECONDS);
    }

    @Test
    @DisplayName("记录失败：首次自增后设置窗口过期，未达上限返回 0（锁定判定基于同次计数）")
    void recordFailureSetsWindowOnFirstFailure() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(KEY)).thenReturn(1L);

        assertEquals(0L, newStore().recordFailureAndCheckLocked(TENANT_ID, USERNAME));
        verify(redisTemplate).expire(KEY, WINDOW_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("记录失败：非首次不再重设过期时间（窗口不因继续试错而延长），未达上限返回 0")
    void recordFailureKeepsWindowAfterFirst() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(KEY)).thenReturn(3L);

        assertEquals(0L, newStore().recordFailureAndCheckLocked(TENANT_ID, USERNAME));
        verify(redisTemplate, never()).expire(anyString(), eq(WINDOW_SECONDS), any(TimeUnit.class));
    }

    @Test
    @DisplayName("记录失败：本次自增触达上限时返回剩余 TTL（计数与判定同次完成，无竞态窗口）")
    void recordFailureLocksAtThreshold() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(KEY)).thenReturn(5L);
        when(redisTemplate.getExpire(KEY, TimeUnit.SECONDS)).thenReturn(42L);

        assertEquals(42L, newStore().recordFailureAndCheckLocked(TENANT_ID, USERNAME));
    }

    @Test
    @DisplayName("锁定判定：达到上限返回剩余 TTL，未达上限返回 0")
    void lockRemainingDependsOnCount() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY)).thenReturn("5");
        when(redisTemplate.getExpire(KEY, TimeUnit.SECONDS)).thenReturn(42L);
        assertEquals(42L, newStore().lockRemainingSeconds(TENANT_ID, USERNAME));

        when(valueOperations.get(KEY)).thenReturn("4");
        assertEquals(0L, newStore().lockRemainingSeconds(TENANT_ID, USERNAME));
    }

    @Test
    @DisplayName("锁定判定：无计数返回 0，TTL 缺失按整窗口兜底而非永久锁定")
    void lockRemainingFallsBackToWindow() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY)).thenReturn(null);
        assertEquals(0L, newStore().lockRemainingSeconds(TENANT_ID, USERNAME));

        when(valueOperations.get(KEY)).thenReturn("9");
        when(redisTemplate.getExpire(KEY, TimeUnit.SECONDS)).thenReturn(-1L);
        assertEquals(WINDOW_SECONDS, newStore().lockRemainingSeconds(TENANT_ID, USERNAME));
    }

    @Test
    @DisplayName("锁定判定：计数脏数据按 0 处理，不把账号永久判为锁定")
    void lockRemainingIgnoresCorruptedCount() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY)).thenReturn("not-a-number");

        assertEquals(0L, newStore().lockRemainingSeconds(TENANT_ID, USERNAME));
    }

    @Test
    @DisplayName("清除：删除计数键")
    void clearDeletesKey() {
        newStore().clear(TENANT_ID, USERNAME);
        verify(redisTemplate).delete(KEY);
    }

    @Test
    @DisplayName("fail-open：Redis 不可用时按未锁定 / 不计数处理，不阻断登录")
    void redisFailureIsFailOpen() {
        long alarmBefore = SecurityDegradationAlerter.alarmCount();
        StringRedisTemplate broken = org.mockito.Mockito.mock(StringRedisTemplate.class);
        when(broken.opsForValue()).thenThrow(new RedisConnectionFailureException("down"));
        RedisLoginAttemptStore store = new RedisLoginAttemptStore(broken, MAX_ATTEMPTS, WINDOW_SECONDS);

        assertEquals(0L, store.recordFailureAndCheckLocked(TENANT_ID, USERNAME));
        assertEquals(0L, store.lockRemainingSeconds(TENANT_ID, USERNAME));
        assertTrue(SecurityDegradationAlerter.alarmCount() > alarmBefore, "应输出安全降级告警");
    }

    @Test
    @DisplayName("fail-open：清除失败不抛异常（由窗口自然过期）")
    void clearFailureIsSwallowed() {
        doThrow(new RedisConnectionFailureException("down")).when(redisTemplate).delete(KEY);
        newStore().clear(TENANT_ID, USERNAME);
        verify(redisTemplate).delete(KEY);
    }

    @Test
    @DisplayName("配置兜底：非法配置回落到下限（上限至少 1 次、窗口至少 1 秒，首次失败即锁定）")
    void illegalConfigIsClamped() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(KEY)).thenReturn(1L);
        when(redisTemplate.getExpire(KEY, TimeUnit.SECONDS)).thenReturn(1L);
        RedisLoginAttemptStore store = new RedisLoginAttemptStore(redisTemplate, 0, 0L);

        // maxAttempts 兜底为 1：首次失败即触达上限，返回兜底窗口 1 秒
        assertEquals(1L, store.recordFailureAndCheckLocked(TENANT_ID, USERNAME));
        verify(redisTemplate).expire(KEY, 1L, TimeUnit.SECONDS);
    }
}
