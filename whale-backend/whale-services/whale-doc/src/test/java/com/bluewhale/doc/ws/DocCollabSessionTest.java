package com.bluewhale.doc.ws;

import com.bluewhale.doc.support.collab.DocPresenceUser;
import com.bluewhale.doc.support.collab.DocWsTicket;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@link DocCollabSession} 属性透传断言。
 *
 * @author BlueWhale
 */
class DocCollabSessionTest {

    private final WebSocketSession ws = mock(WebSocketSession.class);

    private final DocWsTicket ticket = new DocWsTicket(3L, 7L, 9L, "alice");

    @Test
    void shouldExposeTicketFields() {
        DocCollabSession session = new DocCollabSession(ws, ticket, "client-1");

        assertThat(session.getSession()).isSameAs(ws);
        assertThat(session.getTicket()).isSameAs(ticket);
        assertThat(session.getClientId()).isEqualTo("client-1");
        assertThat(session.tenantId()).isEqualTo(3L);
        assertThat(session.pageId()).isEqualTo(9L);
        assertThat(session.userId()).isEqualTo(7L);
    }

    @Test
    void shouldToggleLeaderAndPresence() {
        DocCollabSession session = new DocCollabSession(ws, ticket, "client-1");
        DocPresenceUser presence = new DocPresenceUser();
        presence.setClientId("client-1");

        assertThat(session.isLeader()).isFalse();
        session.setLeader(true);
        assertThat(session.isLeader()).isTrue();

        assertThat(session.getPresence()).isNull();
        session.setPresence(presence);
        assertThat(session.getPresence()).isSameAs(presence);
    }
}
