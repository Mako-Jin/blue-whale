package com.bluewhale.doc.ws;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.doc.application.service.DocCollabService;
import com.bluewhale.doc.config.DocProperties;
import com.bluewhale.doc.domain.exception.DocErrorCode;
import com.bluewhale.doc.support.collab.DocCollabInit;
import com.bluewhale.doc.support.collab.DocCollabSnapshot;
import com.bluewhale.doc.support.collab.DocCollabStore;
import com.bluewhale.doc.support.collab.DocPresenceUser;
import com.bluewhale.doc.support.collab.DocWsTicket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DocCollabWebSocketHandler} 连接生命周期与帧分发行为断言。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocCollabWebSocketHandlerTest {

    @Mock
    private DocCollabService collabService;

    @Mock
    private DocCollabStore collabStore;

    @Mock
    private DocCollabSessionRegistry registry;

    private final DocProperties docProperties = new DocProperties();

    private final ObjectMapper mapper = new ObjectMapper();

    private DocCollabWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        docProperties.setCollabMaxUpdateBytes(1024);
        handler = new DocCollabWebSocketHandler(collabService, collabStore, registry,
                new DocCollabFrames(mapper), docProperties, mapper);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private DocWsTicket ticket() {
        return new DocWsTicket(3L, 7L, 9L, "alice");
    }

    private DocCollabInit init() {
        return new DocCollabInit(9L, "标题", 1L, "DRAFT", null, List.of());
    }

    private WebSocketSession session(Map<String, Object> attributes) throws IOException {
        WebSocketSession ws = mock(WebSocketSession.class);
        when(ws.getAttributes()).thenReturn(attributes);
        when(ws.isOpen()).thenReturn(true);
        when(ws.getId()).thenReturn("ws-1");
        return ws;
    }

    private WebSocketSession connectedSession(String clientId) throws IOException {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(DocCollabAttributes.TICKET, ticket());
        attributes.put(DocCollabAttributes.CLIENT_ID, clientId);
        WebSocketSession ws = session(attributes);
        attributes.put(DocCollabAttributes.SESSION, new DocCollabSession(ws, ticket(), clientId));
        return ws;
    }

    private JsonNode lastSentFrame() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(registry, atLeastOnce()).send(any(), captor.capture());
        return mapper.readTree(captor.getValue());
    }

    private String lastLocalFrame() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(registry, atLeastOnce()).broadcastLocal(any(), any(), captor.capture(), any(), any());
        return captor.getValue();
    }

    private void stubOpenSession() {
        when(collabService.load(any())).thenReturn(init());
        when(collabStore.claimLeader(any(), any(), anyString())).thenReturn(true);
        when(collabStore.presenceUsers(any(), any())).thenReturn(List.of());
        when(collabService.snapshotSeconds()).thenReturn(12);
    }

    @Test
    void establishedShouldCloseWhenTicketMissing() throws IOException {
        WebSocketSession ws = session(new HashMap<>());

        handler.afterConnectionEstablished(ws);

        verify(ws).close(CloseStatus.NOT_ACCEPTABLE);
        verify(collabService, never()).load(any());
    }

    @Test
    void establishedShouldOpenSessionAndSendReady() throws IOException {
        stubOpenSession();
        WebSocketSession ws = connectedSession("client-01");

        handler.afterConnectionEstablished(ws);

        assertThat(ws.getAttributes()).containsKey(DocCollabAttributes.SESSION);
        verify(registry).register(any());
        verify(collabStore).touchPresence(eq(3L), eq(9L), any());
        assertThat(lastSentFrame().get("type").asText()).isEqualTo("ready");
        assertThat(lastSentFrame().get("leader").asBoolean()).isTrue();
        verify(registry).broadcastLocal(eq(3L), eq(9L), anyString(), eq("client-01"), isNull());
        verify(collabStore).publish(eq(3L), eq(9L), anyString(), eq("client-01"), isNull());
        assertThat(TenantContext.get()).isNull();
    }

    @Test
    void establishedShouldGenerateClientIdWhenAttributeTooLong() throws IOException {
        stubOpenSession();
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(DocCollabAttributes.TICKET, ticket());
        attributes.put(DocCollabAttributes.CLIENT_ID, "c".repeat(65));
        WebSocketSession ws = session(attributes);

        handler.afterConnectionEstablished(ws);

        DocCollabSession opened = (DocCollabSession) attributes.get(DocCollabAttributes.SESSION);
        assertThat(opened.getClientId()).hasSize(36).isNotEqualTo("c".repeat(65));
    }

    @Test
    void establishedShouldSendBizErrorAndCloseOnBizException() throws IOException {
        when(collabService.load(any())).thenThrow(DocErrorCode.exception(DocErrorCode.COLLAB_UNAVAILABLE));
        WebSocketSession ws = connectedSession("client-01");

        handler.afterConnectionEstablished(ws);

        JsonNode error = lastSentFrame();
        assertThat(error.get("type").asText()).isEqualTo("error");
        assertThat(error.get("code").asInt()).isEqualTo(DocErrorCode.COLLAB_UNAVAILABLE);
        verify(ws).close(CloseStatus.POLICY_VIOLATION);
        assertThat(TenantContext.get()).isNull();
    }

    @Test
    void establishedShouldSendUnavailableAndCloseOnUnexpectedError() throws IOException {
        when(collabService.load(any())).thenThrow(new IllegalStateException("redis down"));
        WebSocketSession ws = connectedSession("client-01");

        handler.afterConnectionEstablished(ws);

        JsonNode error = lastSentFrame();
        assertThat(error.get("type").asText()).isEqualTo("error");
        assertThat(error.get("code").asInt()).isEqualTo(DocErrorCode.COLLAB_UNAVAILABLE);
        assertThat(error.get("message").asText()).isEqualTo("实时协作暂不可用");
        verify(ws).close(CloseStatus.SERVER_ERROR);
    }

    @Test
    void messageWithoutSessionShouldClosePolicyViolation() throws IOException {
        WebSocketSession ws = session(new HashMap<>());

        handler.handleTextMessage(ws, new TextMessage("{\"type\":\"ping\"}"));

        verify(ws).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    void pingShouldTouchLeaderAndReplyPong() throws IOException {
        when(collabStore.claimLeader(any(), any(), anyString())).thenReturn(true);
        WebSocketSession ws = connectedSession("client-01");

        handler.handleTextMessage(ws, new TextMessage("{\"type\":\"ping\"}"));

        verify(collabStore).touchPresence(eq(3L), eq(9L), any());
        assertThat(mapper.readTree(lastLocalFrame()).get("type").asText()).isEqualTo("leader");
        assertThat(lastSentFrame().get("type").asText()).isEqualTo("pong");
        assertThat(TenantContext.get()).isNull();
    }

    @Test
    void updateShouldRecordAndBroadcast() throws IOException {
        WebSocketSession ws = connectedSession("client-01");

        handler.handleTextMessage(ws, new TextMessage("{\"type\":\"update\",\"update\":\"u1\"}"));

        verify(collabService).recordUpdate(eq(ticket()), eq("u1"));
        assertThat(mapper.readTree(lastLocalFrame()).get("type").asText()).isEqualTo("update");
        verify(collabStore).publish(eq(3L), eq(9L), anyString(), eq("client-01"), isNull());
    }

    @Test
    void seedShouldBeTreatedAsUpdate() throws IOException {
        WebSocketSession ws = connectedSession("client-01");

        handler.handleTextMessage(ws, new TextMessage("{\"type\":\"seed\",\"update\":\"seed\"}"));

        verify(collabService).recordUpdate(eq(ticket()), eq("seed"));
        assertThat(mapper.readTree(lastLocalFrame()).get("type").asText()).isEqualTo("update");
    }

    @Test
    void awarenessShouldDecodeAndBroadcast() throws IOException {
        WebSocketSession ws = connectedSession("client-01");

        handler.handleTextMessage(ws, new TextMessage("{\"type\":\"awareness\",\"update\":\"aGVsbG8=\"}"));

        assertThat(mapper.readTree(lastLocalFrame()).get("type").asText()).isEqualTo("awareness");
        assertThat(mapper.readTree(lastLocalFrame()).get("update").asText()).isEqualTo("aGVsbG8=");
    }

    @Test
    void awarenessShouldRejectIllegalBase64() throws IOException {
        WebSocketSession ws = connectedSession("client-01");

        handler.handleTextMessage(ws, new TextMessage("{\"type\":\"awareness\",\"update\":\"!!!\"}"));

        JsonNode error = lastSentFrame();
        assertThat(error.get("type").asText()).isEqualTo("error");
        assertThat(error.get("code").asInt()).isEqualTo(DocErrorCode.COLLAB_FRAME_INVALID);
        verify(registry, never()).broadcastLocal(any(), any(), anyString(), any(), any());
    }

    @Test
    void awarenessShouldRejectOversizedPayload() throws IOException {
        docProperties.setCollabMaxUpdateBytes(4);
        WebSocketSession ws = connectedSession("client-01");

        handler.handleTextMessage(ws, new TextMessage("{\"type\":\"awareness\",\"update\":\"YWJjZGVm\"}"));

        assertThat(lastSentFrame().get("code").asInt()).isEqualTo(DocErrorCode.COLLAB_FRAME_INVALID);
    }

    @Test
    void snapshotShouldPersistAndBroadcastAckForLeader() throws IOException {
        when(collabStore.claimLeader(any(), any(), anyString())).thenReturn(true);
        when(collabService.snapshot(eq(ticket()), eq("标题"), any()))
                .thenReturn(new DocCollabSnapshot(2L, null, false));
        WebSocketSession ws = connectedSession("client-01");
        DocCollabSession session = (DocCollabSession) ws.getAttributes().get(DocCollabAttributes.SESSION);

        handler.handleTextMessage(ws, new TextMessage(
                "{\"type\":\"snapshot\",\"title\":\"标题\",\"contentJson\":\"{\\\"type\\\":\\\"doc\\\"}\"}"));

        assertThat(session.isLeader()).isTrue();
        verify(collabService).snapshot(eq(ticket()), eq("标题"),
                org.mockito.ArgumentMatchers.<JsonNode>argThat(node -> node.get("type").asText().equals("doc")));
        assertThat(mapper.readTree(lastLocalFrame()).get("type").asText()).isEqualTo("snapshot-ack");
        verify(collabStore).publish(eq(3L), eq(9L), anyString(), isNull(), isNull());
    }

    @Test
    void snapshotShouldRejectNonLeader() throws IOException {
        when(collabStore.claimLeader(any(), any(), anyString())).thenReturn(false);
        WebSocketSession ws = connectedSession("client-01");

        handler.handleTextMessage(ws, new TextMessage("{\"type\":\"snapshot\",\"title\":\"标题\"}"));

        JsonNode frame = lastSentFrame();
        assertThat(frame.get("type").asText()).isEqualTo("leader");
        assertThat(frame.get("leader").asBoolean()).isFalse();
        verify(collabService, never()).snapshot(any(), any(), any());
    }

    @Test
    void leaderClaimShouldBroadcastWhenWon() throws IOException {
        when(collabStore.claimLeader(any(), any(), anyString())).thenReturn(true);
        WebSocketSession ws = connectedSession("client-01");
        DocCollabSession session = (DocCollabSession) ws.getAttributes().get(DocCollabAttributes.SESSION);

        handler.handleTextMessage(ws, new TextMessage("{\"type\":\"leader-claim\"}"));

        assertThat(session.isLeader()).isTrue();
        JsonNode frame = mapper.readTree(lastLocalFrame());
        assertThat(frame.get("type").asText()).isEqualTo("leader");
        assertThat(frame.get("leader").asBoolean()).isTrue();
    }

    @Test
    void leaderClaimShouldOnlyNotifySenderWhenLost() throws IOException {
        when(collabStore.claimLeader(any(), any(), anyString())).thenReturn(false);
        WebSocketSession ws = connectedSession("client-01");

        handler.handleTextMessage(ws, new TextMessage("{\"type\":\"leader-claim\"}"));

        assertThat(lastSentFrame().get("leader").asBoolean()).isFalse();
        verify(registry, never()).broadcastLocal(any(), any(), anyString(), any(), any());
    }

    @Test
    void presenceShouldApplyNameAndColor() throws IOException {
        WebSocketSession ws = connectedSession("client-01");

        handler.handleTextMessage(ws, new TextMessage(
                "{\"type\":\"presence\",\"name\":\" bob \",\"color\":\"#AABBCC\"}"));

        ArgumentCaptor<DocPresenceUser> userCaptor = ArgumentCaptor.forClass(DocPresenceUser.class);
        verify(collabStore).touchPresence(eq(3L), eq(9L), userCaptor.capture());
        assertThat(userCaptor.getValue().getName()).isEqualTo("bob");
        assertThat(userCaptor.getValue().getColor()).isEqualTo("#AABBCC");
        assertThat(mapper.readTree(lastLocalFrame()).get("type").asText()).isEqualTo("presence");
    }

    @Test
    void presenceShouldIgnoreIllegalNameAndColor() throws IOException {
        WebSocketSession ws = connectedSession("client-01");

        handler.handleTextMessage(ws, new TextMessage(
                "{\"type\":\"presence\",\"name\":\"" + "n".repeat(65) + "\",\"color\":\"red\"}"));

        ArgumentCaptor<DocPresenceUser> userCaptor = ArgumentCaptor.forClass(DocPresenceUser.class);
        verify(collabStore).touchPresence(eq(3L), eq(9L), userCaptor.capture());
        assertThat(userCaptor.getValue().getName()).isEqualTo("alice");
        assertThat(userCaptor.getValue().getColor()).isEqualTo("#dc2626");
    }

    @Test
    void illegalJsonShouldSendSystemError() throws IOException {
        WebSocketSession ws = connectedSession("client-01");

        handler.handleTextMessage(ws, new TextMessage("not-json"));

        JsonNode error = lastSentFrame();
        assertThat(error.get("type").asText()).isEqualTo("error");
        assertThat(error.get("code").asInt()).isEqualTo(500);
        assertThat(error.get("message").asText()).isEqualTo("协作帧处理失败");
    }

    @Test
    void missingTypeShouldSendFrameInvalid() throws IOException {
        WebSocketSession ws = connectedSession("client-01");

        handler.handleTextMessage(ws, new TextMessage("{\"update\":\"u\"}"));

        assertThat(lastSentFrame().get("code").asInt()).isEqualTo(DocErrorCode.COLLAB_FRAME_INVALID);
    }

    @Test
    void unknownTypeShouldSendFrameInvalid() throws IOException {
        WebSocketSession ws = connectedSession("client-01");

        handler.handleTextMessage(ws, new TextMessage("{\"type\":\"unknown\"}"));

        assertThat(lastSentFrame().get("code").asInt()).isEqualTo(DocErrorCode.COLLAB_FRAME_INVALID);
    }

    @Test
    void closedShouldCleanupAndElectNewLeader() throws IOException {
        WebSocketSession ws = connectedSession("client-01");
        WebSocketSession otherWs = session(new HashMap<>());
        DocCollabSession candidate = new DocCollabSession(otherWs, ticket(), "client-02");
        when(registry.localSessions(3L, 9L)).thenReturn(List.of(candidate));
        when(collabStore.claimLeader(3L, 9L, "client-02")).thenReturn(true);

        handler.afterConnectionClosed(ws, CloseStatus.NORMAL);

        verify(registry).unregister(any());
        verify(collabStore).removePresence(3L, 9L, "client-01");
        verify(collabStore).releaseLeader(3L, 9L, "client-01");
        assertThat(candidate.isLeader()).isTrue();
        verify(registry, times(2)).broadcastLocal(eq(3L), eq(9L), anyString(), any(), any());
    }

    @Test
    void closedWithoutSessionShouldDoNothing() throws IOException {
        WebSocketSession ws = session(new HashMap<>());

        handler.afterConnectionClosed(ws, CloseStatus.NORMAL);

        verify(registry, never()).unregister(any());
        verify(collabStore, never()).removePresence(any(), any(), anyString());
    }

    @Test
    void pingShouldNotifySenderWhenLeadershipLost() throws IOException {
        when(collabStore.claimLeader(any(), any(), anyString())).thenReturn(false);
        WebSocketSession ws = connectedSession("client-01");
        DocCollabSession session = (DocCollabSession) ws.getAttributes().get(DocCollabAttributes.SESSION);
        session.setLeader(true);

        handler.handleTextMessage(ws, new TextMessage("{\"type\":\"ping\"}"));

        assertThat(session.isLeader()).isFalse();
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(registry, times(2)).send(any(), captor.capture());
        JsonNode leaderFrame = mapper.readTree(captor.getAllValues().get(0));
        assertThat(leaderFrame.get("type").asText()).isEqualTo("leader");
        assertThat(leaderFrame.get("leader").asBoolean()).isFalse();
        assertThat(mapper.readTree(captor.getAllValues().get(1)).get("type").asText()).isEqualTo("pong");
        verify(collabStore).touchPresence(eq(3L), eq(9L), any());
    }

    @Test
    void snapshotShouldPersistNullContentWhenOmitted() throws IOException {
        when(collabStore.claimLeader(any(), any(), anyString())).thenReturn(true);
        when(collabService.snapshot(eq(ticket()), eq("标题"), isNull()))
                .thenReturn(new DocCollabSnapshot(2L, null, false));
        WebSocketSession ws = connectedSession("client-01");

        handler.handleTextMessage(ws, new TextMessage("{\"type\":\"snapshot\",\"title\":\"标题\"}"));

        verify(collabService).snapshot(eq(ticket()), eq("标题"), isNull());
        assertThat(mapper.readTree(lastLocalFrame()).get("type").asText()).isEqualTo("snapshot-ack");
    }

    @Test
    void snapshotShouldAcceptObjectContentJson() throws IOException {
        when(collabStore.claimLeader(any(), any(), anyString())).thenReturn(true);
        when(collabService.snapshot(eq(ticket()), isNull(), any()))
                .thenReturn(new DocCollabSnapshot(2L, null, false));
        WebSocketSession ws = connectedSession("client-01");

        handler.handleTextMessage(ws, new TextMessage(
                "{\"type\":\"snapshot\",\"contentJson\":{\"type\":\"doc\",\"content\":[]}}"));

        verify(collabService).snapshot(eq(ticket()), isNull(),
                org.mockito.ArgumentMatchers.<JsonNode>argThat(node -> "doc".equals(node.get("type").asText())));
        assertThat(mapper.readTree(lastLocalFrame()).get("type").asText()).isEqualTo("snapshot-ack");
    }

    @Test
    void closedShouldSkipElectionWhenNoCandidateClaims() throws IOException {
        WebSocketSession ws = connectedSession("client-01");
        WebSocketSession otherWs = session(new HashMap<>());
        DocCollabSession candidate = new DocCollabSession(otherWs, ticket(), "client-02");
        when(registry.localSessions(3L, 9L)).thenReturn(List.of(candidate));
        when(collabStore.claimLeader(3L, 9L, "client-02")).thenReturn(false);

        handler.afterConnectionClosed(ws, CloseStatus.NORMAL);

        assertThat(candidate.isLeader()).isFalse();
        verify(registry, times(1)).broadcastLocal(eq(3L), eq(9L), anyString(), any(), any());
    }

    @Test
    void transportErrorShouldCloseServerError() throws IOException {
        WebSocketSession ws = session(new HashMap<>());

        handler.handleTransportError(ws, new IllegalStateException("boom"));

        verify(ws).close(CloseStatus.SERVER_ERROR);
    }

    @Test
    void transportErrorShouldSwallowCloseFailure() throws IOException {
        WebSocketSession ws = session(new HashMap<>());
        doThrow(new IOException("broken pipe")).when(ws).close(any());

        handler.handleTransportError(ws, new IllegalStateException("boom"));

        verify(ws).close(CloseStatus.SERVER_ERROR);
    }
}
