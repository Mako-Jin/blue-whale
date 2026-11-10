package com.bluewhale.assistant.service;

import com.bluewhale.assistant.config.AssistantProperties;
import com.bluewhale.assistant.dto.ChatRequest;
import com.bluewhale.assistant.dto.OpenChatRequest;
import com.bluewhale.assistant.dto.OpenChatVO;
import com.bluewhale.assistant.dto.SessionCreateRequest;
import com.bluewhale.assistant.entity.AsstDefinition;
import com.bluewhale.assistant.entity.AsstMessage;
import com.bluewhale.assistant.entity.AsstSession;
import com.bluewhale.assistant.support.AssistantStreamClient;
import com.bluewhale.common.core.exception.BizException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 助手问答服务：SSE 透传、助手配置下发、落库与上游失败处理。
 *
 * @author BlueWhale
 */
class AssistantChatServiceTest {

    private static final String SUCCESS_SSE = """
            event: meta
            data: {"conversationId":"5","assistantId":"mes-helper","intent":"SEMANTIC","degraded":false,"citations":[]}

            event: tool
            data: {"name":"knowledge_search","status":"ok","count":1}

            event: delta
            data: {"text":"网关 [S1]。"}

            event: done
            data: {"tokens":12,"degraded":false,"citations":[{"sourceId":"S1","kind":"chunk","path":"docs/a.md","startLine":1,"endLine":2,"snippet":"x"}]}

            """;

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void relaysAnswerPersistsCitationsAndUsage() throws IOException {
        AsstSessionService sessionService = mock(AsstSessionService.class);
        AsstDefinitionService definitionService = mock(AsstDefinitionService.class);
        AsstUsageService usageService = mock(AsstUsageService.class);
        AsstSession session = session();
        when(sessionService.requireOwned(5L, 7L)).thenReturn(session);
        when(definitionService.requirePublished(9L)).thenReturn(definition());
        when(definitionService.toolWhitelist(9L)).thenReturn(List.of("knowledge_search"));
        AtomicLong messageIds = new AtomicLong(100);
        when(sessionService.append(anyLong(), anyString(), anyString(), any(), anyInt(), anyInt(), anyInt()))
                .thenAnswer(invocation -> {
                    AsstMessage message = new AsstMessage();
                    message.setId(messageIds.getAndIncrement());
                    return message;
                });

        List<String> requestBodies = new CopyOnWriteArrayList<>();
        AssistantChatService service = service(sessionService, definitionService, usageService,
                200, SUCCESS_SSE, requestBodies);

        SseEmitter emitter = service.chat(5L, request(), 7L, "sys:assistant:chat");

        assertNotNull(emitter);
        verify(sessionService, timeout(3000))
                .append(eq(5L), eq(AsstSessionService.ROLE_ASSISTANT), eq("网关 [S1]。"),
                        contains("S1"), eq(12), anyInt(), anyInt());
        verify(usageService, timeout(3000)).recordAnswer(eq(9L), eq(12));
        verify(sessionService, timeout(3000)).touch(session);
        // 助手配置随请求下发 whale-ai
        assertTrue(requestBodies.get(0).contains("\"assistantId\":\"mes-helper\""));
        assertTrue(requestBodies.get(0).contains("\"systemPrompt\":\"你是 MES 助手。\""));
        assertTrue(requestBodies.get(0).contains("\"toolWhitelist\":[\"knowledge_search\"]"));
    }

    @Test
    void reportsErrorWhenUpstreamUnavailable() throws Exception {
        AsstSessionService sessionService = mock(AsstSessionService.class);
        AsstDefinitionService definitionService = mock(AsstDefinitionService.class);
        AsstSession session = session();
        when(sessionService.requireOwned(anyLong(), anyLong())).thenReturn(session);
        when(definitionService.requirePublished(9L)).thenReturn(definition());
        when(definitionService.toolWhitelist(9L)).thenReturn(List.of());
        when(sessionService.append(anyLong(), anyString(), anyString(), any(), anyInt(), anyInt(), anyInt()))
                .thenAnswer(invocation -> {
                    AsstMessage message = new AsstMessage();
                    message.setId(100L);
                    return message;
                });
        AssistantChatService service = service(sessionService, definitionService,
                mock(AsstUsageService.class), 503, "", new CopyOnWriteArrayList<>());

        service.chat(5L, request(), 7L, "sys:assistant:chat");

        verify(sessionService, timeout(3000))
                .append(eq(5L), eq(AsstSessionService.ROLE_USER), anyString(), isNull(),
                        eq(0), eq(0), eq(0));
        Thread.sleep(500);
        verify(sessionService, never())
                .append(eq(5L), eq(AsstSessionService.ROLE_ASSISTANT), anyString(), any(),
                        anyInt(), anyInt(), anyInt());
    }

    @Test
    void chatSyncAggregatesAnswerPersistsAndCreatesOpenapiSession() throws IOException {
        AsstSessionService sessionService = mock(AsstSessionService.class);
        AsstDefinitionService definitionService = mock(AsstDefinitionService.class);
        AsstUsageService usageService = mock(AsstUsageService.class);
        AsstSession created = session();
        when(definitionService.requirePublishedByCode("mes-helper")).thenReturn(definition());
        when(definitionService.toolWhitelist(9L)).thenReturn(List.of("knowledge_search"));
        when(sessionService.createSession(any(SessionCreateRequest.class), eq(7L))).thenReturn(created);
        AtomicLong messageIds = new AtomicLong(100);
        when(sessionService.append(anyLong(), anyString(), anyString(), any(), anyInt(), anyInt(), anyInt()))
                .thenAnswer(invocation -> {
                    AsstMessage message = new AsstMessage();
                    message.setId(messageIds.getAndIncrement());
                    return message;
                });

        List<String> requestBodies = new CopyOnWriteArrayList<>();
        AssistantChatService service = service(sessionService, definitionService, usageService,
                200, SUCCESS_SSE, requestBodies);

        OpenChatRequest openRequest = openRequest();
        OpenChatVO result = service.chatSync(openRequest, 7L, "sys:assistant:chat");

        assertEquals("5", result.getSessionId());
        assertEquals("101", result.getMessageId());
        assertEquals("网关 [S1]。", result.getAnswer());
        assertEquals(12, result.getTokens());
        assertEquals(1, result.getCitations().size());
        // 会话来源固定 openapi
        org.mockito.ArgumentCaptor<SessionCreateRequest> captor =
                org.mockito.ArgumentCaptor.forClass(SessionCreateRequest.class);
        verify(sessionService).createSession(captor.capture(), eq(7L));
        assertEquals(AsstSessionService.SOURCE_OPENAPI, captor.getValue().getSource());
        verify(sessionService).append(eq(5L), eq(AsstSessionService.ROLE_ASSISTANT),
                eq("网关 [S1]。"), contains("S1"), eq(12), anyInt(), anyInt());
        verify(usageService).recordAnswer(eq(9L), eq(12));
        // 助手配置随请求下发 whale-ai
        assertTrue(requestBodies.get(0).contains("\"assistantId\":\"mes-helper\""));
        assertTrue(requestBodies.get(0).contains("\"toolWhitelist\":[\"knowledge_search\"]"));
    }

    @Test
    void chatSyncReusesExistingSessionWhenOwnedAndMatched() throws IOException {
        AsstSessionService sessionService = mock(AsstSessionService.class);
        AsstDefinitionService definitionService = mock(AsstDefinitionService.class);
        AsstUsageService usageService = mock(AsstUsageService.class);
        AsstSession existing = session();
        when(definitionService.requirePublishedByCode("mes-helper")).thenReturn(definition());
        when(sessionService.requireOwned(5L, 7L)).thenReturn(existing);
        when(sessionService.append(anyLong(), anyString(), anyString(), any(), anyInt(), anyInt(), anyInt()))
                .thenAnswer(invocation -> {
                    AsstMessage message = new AsstMessage();
                    message.setId(200L);
                    return message;
                });

        AssistantChatService service = service(sessionService, definitionService, usageService,
                200, SUCCESS_SSE, new CopyOnWriteArrayList<>());

        OpenChatRequest openRequest = openRequest();
        openRequest.setSessionId(5L);
        OpenChatVO result = service.chatSync(openRequest, 7L, null);

        assertEquals("5", result.getSessionId());
        verify(sessionService, never()).createSession(any(), anyLong());
    }

    @Test
    void chatSyncRejectsSessionAssistantMismatch() throws IOException {
        AsstSessionService sessionService = mock(AsstSessionService.class);
        AsstDefinitionService definitionService = mock(AsstDefinitionService.class);
        AsstSession existing = session();
        existing.setAssistantId(8L);
        when(definitionService.requirePublishedByCode("mes-helper")).thenReturn(definition());
        when(sessionService.requireOwned(5L, 7L)).thenReturn(existing);

        AssistantChatService service = service(sessionService, definitionService,
                mock(AsstUsageService.class), 200, SUCCESS_SSE, new CopyOnWriteArrayList<>());

        OpenChatRequest openRequest = openRequest();
        openRequest.setSessionId(5L);
        assertThrows(BizException.class, () -> service.chatSync(openRequest, 7L, null));
    }

    @Test
    void chatSyncThrowsBizWhenUpstreamUnavailable() throws IOException {
        AsstSessionService sessionService = mock(AsstSessionService.class);
        AsstDefinitionService definitionService = mock(AsstDefinitionService.class);
        when(definitionService.requirePublishedByCode("mes-helper")).thenReturn(definition());
        when(sessionService.createSession(any(SessionCreateRequest.class), eq(7L))).thenReturn(session());
        when(sessionService.append(anyLong(), anyString(), anyString(), any(), anyInt(), anyInt(), anyInt()))
                .thenAnswer(invocation -> {
                    AsstMessage message = new AsstMessage();
                    message.setId(100L);
                    return message;
                });

        AssistantChatService service = service(sessionService, definitionService,
                mock(AsstUsageService.class), 503, "", new CopyOnWriteArrayList<>());

        BizException exception = assertThrows(BizException.class,
                () -> service.chatSync(openRequest(), 7L, null));
        assertTrue(exception.getMessage().contains("智能体引擎"));
    }

    private AssistantChatService service(AsstSessionService sessionService,
                                         AsstDefinitionService definitionService,
                                         AsstUsageService usageService,
                                         int status, String body,
                                         List<String> requestBodies) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/agent/stream", exchange -> {
            requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        AssistantProperties properties = new AssistantProperties();
        properties.getAi().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        return new AssistantChatService(sessionService, definitionService, usageService,
                new AssistantStreamClient(properties), properties);
    }

    private static AsstSession session() {
        AsstSession session = new AsstSession();
        session.setId(5L);
        session.setAssistantId(9L);
        session.setUserId(7L);
        session.setTitle("新会话");
        session.setSource(AsstSessionService.SOURCE_PLATFORM);
        return session;
    }

    private static AsstDefinition definition() {
        AsstDefinition definition = new AsstDefinition();
        definition.setId(9L);
        definition.setCode("mes-helper");
        definition.setName("MES 助手");
        definition.setSystemPrompt("你是 MES 助手。");
        definition.setModelParamsJson("{\"model\":\"gpt-4o-mini\",\"temperature\":0.3}");
        definition.setKnowledgeScope("doc,code");
        definition.setStatus(AsstDefinitionService.STATUS_PUBLISHED);
        return definition;
    }

    private static ChatRequest request() {
        ChatRequest request = new ChatRequest();
        request.setQuestion("网关端口？");
        return request;
    }

    private static OpenChatRequest openRequest() {
        OpenChatRequest request = new OpenChatRequest();
        request.setAssistantCode("mes-helper");
        request.setQuestion("网关端口？");
        request.setChannelRef("crm-deal-42");
        return request;
    }
}
