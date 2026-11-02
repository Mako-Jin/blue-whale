package com.bluewhale.doc.ws;

import com.bluewhale.doc.support.collab.DocWsTicket;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DocCollabSessionRegistry} 分组、投递与异常吞掉行为断言。
 *
 * @author BlueWhale
 */
class DocCollabSessionRegistryTest {

    private final DocCollabSessionRegistry registry = new DocCollabSessionRegistry();

    private static DocCollabSession session(WebSocketSession ws, Long tenantId, Long pageId, String clientId) {
        return new DocCollabSession(ws, new DocWsTicket(tenantId, 7L, pageId, "alice"), clientId);
    }

    private static WebSocketSession openSession() throws IOException {
        WebSocketSession ws = mock(WebSocketSession.class);
        when(ws.isOpen()).thenReturn(true);
        when(ws.getId()).thenReturn("ws-1");
        return ws;
    }

    @Test
    void registerShouldGroupByTenantAndPage() throws IOException {
        DocCollabSession first = session(openSession(), 3L, 9L, "c1");
        DocCollabSession second = session(openSession(), 3L, 9L, "c2");
        registry.register(first);
        registry.register(second);

        assertThat(registry.localCount(3L, 9L)).isEqualTo(2);
        assertThat(registry.localSessions(3L, 9L)).containsExactlyInAnyOrder(first, second);
        assertThat(registry.localCount(3L, 10L)).isZero();
        assertThat(registry.localSessions(4L, 9L)).isEmpty();
    }

    @Test
    void unregisterShouldRemoveSessionAndEmptyGroup() throws IOException {
        DocCollabSession first = session(openSession(), 3L, 9L, "c1");
        DocCollabSession second = session(openSession(), 3L, 9L, "c2");
        registry.register(first);
        registry.register(second);

        registry.unregister(first);
        assertThat(registry.localCount(3L, 9L)).isEqualTo(1);

        registry.unregister(second);
        assertThat(registry.localSessions(3L, 9L)).isEmpty();
        assertThat(registry.localCount(3L, 9L)).isZero();
    }

    @Test
    void unregisterShouldIgnoreUnknownGroup() throws IOException {
        DocCollabSession session = session(openSession(), 3L, 9L, "c1");

        registry.unregister(session);

        assertThat(registry.localSessions(3L, 9L)).isEmpty();
    }

    @Test
    void broadcastLocalShouldSkipExcludedClient() throws IOException {
        WebSocketSession firstWs = openSession();
        WebSocketSession secondWs = openSession();
        registry.register(session(firstWs, 3L, 9L, "c1"));
        registry.register(session(secondWs, 3L, 9L, "c2"));

        registry.broadcastLocal(3L, 9L, "frame", "c1", null);

        verify(firstWs, never()).sendMessage(any());
        verify(secondWs).sendMessage(any(TextMessage.class));
    }

    @Test
    void broadcastLocalShouldOnlySendToTargetClient() throws IOException {
        WebSocketSession firstWs = openSession();
        WebSocketSession secondWs = openSession();
        registry.register(session(firstWs, 3L, 9L, "c1"));
        registry.register(session(secondWs, 3L, 9L, "c2"));

        registry.broadcastLocal(3L, 9L, "frame", null, "c2");

        verify(firstWs, never()).sendMessage(any());
        verify(secondWs).sendMessage(any(TextMessage.class));
    }

    @Test
    void broadcastLocalShouldSendToAllWhenNoFilters() throws IOException {
        WebSocketSession firstWs = openSession();
        WebSocketSession secondWs = openSession();
        registry.register(session(firstWs, 3L, 9L, "c1"));
        registry.register(session(secondWs, 3L, 9L, "c2"));

        registry.broadcastLocal(3L, 9L, "frame", null, null);

        verify(firstWs).sendMessage(any(TextMessage.class));
        verify(secondWs).sendMessage(any(TextMessage.class));
    }

    @Test
    void sendShouldIgnoreNullAndClosedSession() throws IOException {
        registry.send(session(null, 3L, 9L, "c1"), "frame");

        WebSocketSession closed = mock(WebSocketSession.class);
        when(closed.isOpen()).thenReturn(false);
        registry.send(session(closed, 3L, 9L, "c2"), "frame");

        verify(closed, never()).sendMessage(any());
    }

    @Test
    void sendShouldSwallowIoException() throws IOException {
        WebSocketSession ws = openSession();
        doThrow(new IOException("broken pipe")).when(ws).sendMessage(any());

        registry.send(session(ws, 3L, 9L, "c1"), "frame");

        verify(ws).sendMessage(any(TextMessage.class));
    }

    @Test
    void localSessionsShouldReturnIndependentSnapshot() throws IOException {
        DocCollabSession session = session(openSession(), 3L, 9L, "c1");
        registry.register(session);

        List<DocCollabSession> snapshot = registry.localSessions(3L, 9L);
        snapshot.clear();

        assertThat(registry.localCount(3L, 9L)).isEqualTo(1);
    }
}
