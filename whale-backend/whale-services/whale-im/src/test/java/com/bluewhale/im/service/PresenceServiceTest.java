package com.bluewhale.im.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PresenceService} 单元测试：在线续期 TTL 与离线清理。
 *
 * @author BlueWhale
 */
class PresenceServiceTest {

    private static final long USER_ID = 10L;

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);

    private final PresenceService service = new PresenceService(redisTemplate, 90L);

    @Test
    @DisplayName("touch：写入 whale:im:presence: 键并设置 90 秒 TTL")
    void touchShouldSetPresenceWithTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        service.touch(USER_ID);

        verify(valueOperations).set("whale:im:presence:10", "1", Duration.ofSeconds(90));
    }

    @Test
    @DisplayName("offline：删除在线状态键")
    void offlineShouldDeletePresence() {
        service.offline(USER_ID);

        verify(redisTemplate).delete("whale:im:presence:10");
    }

    @Test
    @DisplayName("touch：Redis 异常不向调用方抛出")
    void touchShouldSwallowRedisFailure() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new RuntimeException("down")).when(valueOperations)
                .set(anyString(), anyString(), eq(Duration.ofSeconds(90)));

        assertDoesNotThrow(() -> service.touch(USER_ID));
    }
}
