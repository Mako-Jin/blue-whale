package com.bluewhale.agent.service;

import com.bluewhale.agent.config.AgentProperties;
import com.bluewhale.agent.dto.ChatRequest;
import com.bluewhale.agent.entity.AgentConversation;
import com.bluewhale.agent.entity.AgentMessage;
import com.bluewhale.agent.support.langchain.LangchainStreamClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertNotNull;
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
 * LangChain 引擎问答：SSE 透传、引用落库与上游失败处理。
 *
 * @author BlueWhale
 */
class LangchainChatServiceTest {

    private static final String SUCCESS_SSE = """
            event: meta
            data: {"conversationId":"5","intent":"SEMANTIC","degraded":false,"citations":[]}

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
    void relaysAnswerAndPersistsCitations() throws IOException {
        AgentConversationService conversationService = mock(AgentConversationService.class);
        AgentConversation conversation = new AgentConversation();
        conversation.setId(5L);
        when(conversationService.resolve(isNull(), eq(7L), anyString(), isNull())).thenReturn(conversation);
        AtomicLong messageIds = new AtomicLong(100);
        when(conversationService.append(anyLong(), anyString(), anyString(), any(), anyInt()))
                .thenAnswer(invocation -> {
                    AgentMessage message = new AgentMessage();
                    message.setId(messageIds.getAndIncrement());
                    return message;
                });
        LangchainChatService service = service(conversationService, 200, SUCCESS_SSE);

        SseEmitter emitter = service.chat(request(), 7L, "sys:agent:chat");

        assertNotNull(emitter);
        verify(conversationService, timeout(3000))
                .append(eq(5L), eq(AgentConversationService.ROLE_ASSISTANT), eq("网关 [S1]。"), contains("S1"), eq(12));
        verify(conversationService, timeout(3000)).touch(conversation);
    }

    @Test
    void reportsErrorWhenUpstreamUnavailable() throws Exception {
        AgentConversationService conversationService = mock(AgentConversationService.class);
        AgentConversation conversation = new AgentConversation();
        conversation.setId(5L);
        when(conversationService.resolve(any(), any(), anyString(), any())).thenReturn(conversation);
        LangchainChatService service = service(conversationService, 503, "");

        service.chat(request(), 7L, "sys:agent:chat");

        verify(conversationService, timeout(3000))
                .append(eq(5L), eq(AgentConversationService.ROLE_USER), anyString(), isNull(), eq(0));
        Thread.sleep(500);
        verify(conversationService, never())
                .append(eq(5L), eq(AgentConversationService.ROLE_ASSISTANT), anyString(), any(), anyInt());
    }

    private LangchainChatService service(AgentConversationService conversationService, int status,
                                         String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/agent/stream", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        AgentProperties properties = new AgentProperties();
        properties.getQa().getLangchain().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        return new LangchainChatService(conversationService,
                new LangchainStreamClient(properties), properties);
    }

    private static ChatRequest request() {
        ChatRequest request = new ChatRequest();
        request.setQuestion("网关端口？");
        return request;
    }
}
