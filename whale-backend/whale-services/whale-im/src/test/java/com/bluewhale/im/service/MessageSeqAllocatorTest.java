package com.bluewhale.im.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link MessageSeqAllocator} 单元测试：Redis 正常路径与不可用降级路径。
 *
 * @author BlueWhale
 */
class MessageSeqAllocatorTest {

    private static final long CONVERSATION_ID = 100L;

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);

    private final MessageSeqAllocator allocator = new MessageSeqAllocator(redisTemplate);

    @Test
    @DisplayName("next：Redis 可用时以 INCR 结果为准")
    void shouldUseRedisIncrement() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(7L);

        assertEquals(7L, allocator.next(CONVERSATION_ID, 3L));
    }

    @Test
    @DisplayName("next：INCR 返回空值时降级为库内序号 + 1")
    void shouldFallbackWhenIncrementIsNull() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(null);

        assertEquals(4L, allocator.next(CONVERSATION_ID, 3L));
    }

    @Test
    @DisplayName("next：Redis 连接失败时降级为库内序号 + 1")
    void shouldFallbackWhenRedisDown() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenThrow(new RedisConnectionFailureException("down"));

        assertEquals(4L, allocator.next(CONVERSATION_ID, 3L));
    }
}
