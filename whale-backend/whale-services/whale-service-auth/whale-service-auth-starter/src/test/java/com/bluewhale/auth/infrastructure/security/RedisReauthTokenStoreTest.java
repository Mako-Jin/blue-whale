package com.bluewhale.auth.infrastructure.security;

import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * {@link RedisReauthTokenStore} 单元测试（Wave 6.22）
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("step-up 凭证存储")
class RedisReauthTokenStoreTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisReauthTokenStore newStore() {
        return new RedisReauthTokenStore(redisTemplate, 300);
    }

    @Test
    @DisplayName("发放：写入 (租户,用户) 键并返回 64 位十六进制凭证")
    void issueWritesTokenWithTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String token = newStore().issue(1L, 2L);
        assertEquals(64, token.length());
        assertTrue(token.matches("[0-9a-f]{64}"));
    }

    @Test
    @DisplayName("发放：每次生成不同凭证（旧凭证被覆盖）")
    void issueGeneratesDistinctTokens() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        RedisReauthTokenStore store = newStore();
        assertNotEquals(store.issue(1L, 2L), store.issue(1L, 2L));
    }

    @Test
    @DisplayName("校验：匹配返回 true，不匹配/空值返回 false")
    void matchesComparesConstantTime() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String token = newStore().issue(1L, 2L);
        when(valueOperations.get("whale:auth:reauth:1:2")).thenReturn(token);
        RedisReauthTokenStore store = newStore();
        assertTrue(store.matches(1L, 2L, token));
        assertFalse(store.matches(1L, 2L, "deadbeef"));
        assertFalse(store.matches(1L, 2L, null));
        when(valueOperations.get("whale:auth:reauth:1:3")).thenReturn(null);
        assertFalse(store.matches(1L, 3L, token));
    }

    @Test
    @DisplayName("fail-closed：Redis 不可用时发放抛 BizException、校验返回 false")
    void redisFailureIsFailClosed() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenThrow(new RedisConnectionFailureException("down"));
        RedisReauthTokenStore store = newStore();
        assertFalse(store.matches(1L, 2L, "token"));

        StringRedisTemplate broken = org.mockito.Mockito.mock(StringRedisTemplate.class);
        when(broken.opsForValue()).thenThrow(new RedisConnectionFailureException("down"));
        assertThrows(BizException.class, () -> new RedisReauthTokenStore(broken, 300).issue(1L, 2L));
    }

    @Test
    @DisplayName("TTL：非法值回落最小值 1 秒，正常值原样透出")
    void ttlIsClamped() {
        assertEquals(1L, new RedisReauthTokenStore(redisTemplate, 0).ttlSeconds());
        assertEquals(300L, newStore().ttlSeconds());
    }
}
