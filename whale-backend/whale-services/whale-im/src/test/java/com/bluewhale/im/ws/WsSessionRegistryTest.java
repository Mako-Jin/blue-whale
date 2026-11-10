package com.bluewhale.im.ws;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link WsSessionRegistry} 单元测试：多端注册、最后连接判定、断链摘除。
 *
 * @author BlueWhale
 */
class WsSessionRegistryTest {

    private static final long USER_ID = 10L;

    /** 与 {@link WsSessionRegistry} 的单用户连接上限保持一致 */
    private static final int MAX_SESSIONS = 5;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private final WsSessionRegistry registry = new WsSessionRegistry(objectMapper);

    private final WebSocketSession session = mock(WebSocketSession.class);

    /**
     * 默认会话处于打开状态
     */
    @BeforeEach
    void setUp() {
        when(session.isOpen()).thenReturn(true);
    }

    @Test
    @DisplayName("send：按用户推送已序列化的 JSON 帧")
    void sendShouldDeliverJsonFrame() throws IOException {
        registry.register(USER_ID, session);

        registry.send(USER_ID, WsFrames.pong());

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        assertTrue(captor.getValue().getPayload().contains("\"type\":\"pong\""));
    }

    @Test
    @DisplayName("unregister：仅最后一个连接断开时返回 true")
    void unregisterShouldReportLastSession() {
        WebSocketSession second = mock(WebSocketSession.class);
        registry.register(USER_ID, session);
        registry.register(USER_ID, second);

        assertFalse(registry.unregister(USER_ID, session));
        assertTrue(registry.unregister(USER_ID, second));
    }

    @Test
    @DisplayName("register：单用户超过连接上限时拒绝新连接")
    void registerShouldRejectOverLimit() {
        for (int i = 0; i < MAX_SESSIONS; i++) {
            assertTrue(registry.register(USER_ID, mock(WebSocketSession.class)));
        }

        assertFalse(registry.register(USER_ID, mock(WebSocketSession.class)));
    }

    @Test
    @DisplayName("send：发送失败时摘除会话，后续不再重复推送")
    void sendShouldDropBrokenSession() throws IOException {
        registry.register(USER_ID, session);
        doThrow(new IOException("broken")).when(session).sendMessage(any(TextMessage.class));

        registry.send(USER_ID, WsFrames.pong());
        registry.send(USER_ID, WsFrames.pong());

        verify(session, times(1)).sendMessage(any(TextMessage.class));
    }
}
