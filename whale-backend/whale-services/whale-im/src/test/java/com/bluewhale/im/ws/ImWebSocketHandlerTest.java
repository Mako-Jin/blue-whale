package com.bluewhale.im.ws;

import com.bluewhale.im.service.PresenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link ImWebSocketHandler} 单元测试：就绪帧、心跳应答、未知帧、在线状态生命周期。
 *
 * @author BlueWhale
 */
class ImWebSocketHandlerTest {

    private static final long USER_ID = 10L;

    private static final long HEARTBEAT_SECONDS = 30L;

    private final WsSessionRegistry registry = mock(WsSessionRegistry.class);

    private final PresenceService presenceService = mock(PresenceService.class);

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private final ImWebSocketHandler handler =
            new ImWebSocketHandler(registry, presenceService, objectMapper, HEARTBEAT_SECONDS);

    private final WebSocketSession session = mock(WebSocketSession.class);

    /**
     * 构造已认证会话桩
     */
    @BeforeEach
    void setUp() {
        when(session.getAttributes()).thenReturn(new HashMap<>(Map.of(ImWebSocketHandler.ATTR_USER_ID, USER_ID)));
        when(session.getId()).thenReturn("s-1");
    }

    @Test
    @DisplayName("连接建立：注册会话、标记在线并下发 ready 首帧")
    void connectShouldRegisterAndSendReady() {
        when(registry.register(USER_ID, session)).thenReturn(true);

        handler.afterConnectionEstablished(session);

        verify(registry).register(USER_ID, session);
        verify(presenceService).touch(USER_ID);
        ArgumentCaptor<Map<String, Object>> captor = frameCaptor();
        verify(registry).send(eq(USER_ID), captor.capture());
        assertEquals(WsFrames.TYPE_READY, captor.getValue().get("type"));
        assertEquals("10", captor.getValue().get("userId"));
        assertEquals(HEARTBEAT_SECONDS, captor.getValue().get("heartbeatSeconds"));
    }

    @Test
    @DisplayName("ping 帧：续期在线状态并回 pong")
    void pingShouldReplyPong() throws Exception {
        handler.handleMessage(session, new TextMessage("{\"type\":\"ping\"}"));

        verify(presenceService).touch(USER_ID);
        ArgumentCaptor<Map<String, Object>> captor = frameCaptor();
        verify(registry).send(eq(USER_ID), captor.capture());
        assertEquals(WsFrames.TYPE_PONG, captor.getValue().get("type"));
    }

    @Test
    @DisplayName("未知帧：返回协议错误帧")
    void unknownFrameShouldReturnError() throws Exception {
        handler.handleMessage(session, new TextMessage("{\"type\":\"subscribe\"}"));

        ArgumentCaptor<Map<String, Object>> captor = frameCaptor();
        verify(registry).send(eq(USER_ID), captor.capture());
        assertEquals(WsFrames.TYPE_ERROR, captor.getValue().get("type"));
    }

    @Test
    @DisplayName("连接关闭：最后一个连接断开时清除在线状态")
    void closeShouldClearPresenceWhenLastSession() {
        when(registry.unregister(USER_ID, session)).thenReturn(true);

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        verify(presenceService).offline(USER_ID);
    }

    @Test
    @DisplayName("连接关闭：仍有其他端在线时保留在线状态")
    void closeShouldKeepPresenceWhenOtherSessionsRemain() {
        when(registry.unregister(USER_ID, session)).thenReturn(false);

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        verify(presenceService, never()).offline(USER_ID);
    }

    @Test
    @DisplayName("会话缺少认证信息：直接关闭连接且不注册")
    void unauthenticatedSessionShouldBeClosed() throws Exception {
        when(session.getAttributes()).thenReturn(new HashMap<>());

        handler.afterConnectionEstablished(session);

        verify(session).close(CloseStatus.POLICY_VIOLATION);
        verifyNoInteractions(registry);
        verifyNoInteractions(presenceService);
    }

    /**
     * 构造帧参数捕获器
     *
     * @return 帧捕获器
     */
    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Map<String, Object>> frameCaptor() {
        return ArgumentCaptor.forClass(Map.class);
    }
}
