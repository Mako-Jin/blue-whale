package com.bluewhale.oa.integration.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link OutboxEvent} 重试与终态单元测试。
 *
 * @author BlueWhale
 */
class OutboxEventTest {

    @Test
    @DisplayName("markSent：清除失败信息与下次重试时间")
    void shouldMarkSent() {
        OutboxEvent event = pendingEvent();
        event.setErrorMsg("上次失败");
        event.setNextRetryTime(java.time.LocalDateTime.now());

        event.markSent();

        assertEquals(OutboxStatus.SENT, event.getStatus());
        assertNotNull(event.getSentAt());
        assertNull(event.getErrorMsg());
        assertNull(event.getNextRetryTime());
    }

    @Test
    @DisplayName("markFailed：前四次退避重试，第五次转死信")
    void shouldEscalateToDeadAfterMaxRetry() {
        OutboxEvent event = pendingEvent();

        for (int i = 0; i < 4; i++) {
            event.markFailed("第 " + (i + 1) + " 次失败");
            assertEquals(OutboxStatus.FAILED, event.getStatus());
            assertNotNull(event.getNextRetryTime());
        }

        event.markFailed("第 5 次失败");

        assertEquals(OutboxStatus.DEAD, event.getStatus());
        assertEquals(5, event.getRetryCount());
        assertNull(event.getNextRetryTime());
    }

    @Test
    @DisplayName("markFailed：已发送事件不再降级")
    void shouldNotDowngradeSentEvent() {
        OutboxEvent event = pendingEvent();
        event.markSent();

        event.markFailed("不应该生效");

        assertEquals(OutboxStatus.SENT, event.getStatus());
        assertEquals(0, event.getRetryCount());
    }

    /**
     * 构造待发送事件。
     *
     * @return 事件
     */
    private static OutboxEvent pendingEvent() {
        OutboxEvent event = new OutboxEvent();
        event.setEventKey("LEAVE_APPROVED_1");
        event.setStatus(OutboxStatus.PENDING);
        return event;
    }
}
