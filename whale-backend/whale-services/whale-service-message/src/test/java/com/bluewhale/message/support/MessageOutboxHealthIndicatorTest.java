package com.bluewhale.message.support;

import com.bluewhale.message.config.MessageReceiptProperties;
import com.bluewhale.message.mapper.MessageCallbackTaskMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link MessageOutboxHealthIndicator} 积压健康检查单元测试。
 *
 * @author BlueWhale
 */
class MessageOutboxHealthIndicatorTest {

    private final MessageCallbackTaskMapper callbackTaskMapper = mock(MessageCallbackTaskMapper.class);

    private final MessageReceiptProperties properties = new MessageReceiptProperties();

    private final MessageOutboxHealthIndicator indicator =
            new MessageOutboxHealthIndicator(callbackTaskMapper, properties);

    @Test
    @DisplayName("积压未超阈值时健康")
    void shouldBeUpWhenPendingBelowThreshold() {
        properties.setPendingAlertThreshold(1000L);
        when(callbackTaskMapper.selectCount(any())).thenReturn(10L);

        Health health = indicator.health();

        assertEquals("UP", health.getStatus().getCode());
        assertEquals(10L, health.getDetails().get("pending"));
    }

    @Test
    @DisplayName("积压超过阈值时 DOWN")
    void shouldBeDownWhenPendingExceedsThreshold() {
        properties.setPendingAlertThreshold(1000L);
        when(callbackTaskMapper.selectCount(any())).thenReturn(1001L);

        Health health = indicator.health();

        assertEquals("DOWN", health.getStatus().getCode());
        assertEquals(1001L, health.getDetails().get("pending"));
    }

    @Test
    @DisplayName("计数为空按 0 处理")
    void shouldTreatNullCountAsZero() {
        when(callbackTaskMapper.selectCount(any())).thenReturn(null);

        Health health = indicator.health();

        assertEquals("UP", health.getStatus().getCode());
        assertEquals(0L, health.getDetails().get("pending"));
    }
}
