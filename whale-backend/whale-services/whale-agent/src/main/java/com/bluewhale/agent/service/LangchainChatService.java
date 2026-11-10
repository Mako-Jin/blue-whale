package com.bluewhale.agent.service;

import com.bluewhale.agent.config.AgentProperties;
import com.bluewhale.agent.dto.ChatDoneVO;
import com.bluewhale.agent.dto.ChatRequest;
import com.bluewhale.agent.dto.CitationVO;
import com.bluewhale.agent.entity.AgentConversation;
import com.bluewhale.agent.entity.AgentMessage;
import com.bluewhale.agent.support.langchain.LangchainStreamClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * LangChain 引擎问答：把 {@code /agent/chat} 委托给 ai/whale-ai，并保持既有 SSE 契约。
 *
 * <p>事件映射：Python 的 {@code meta/tool/delta} 原样透传；{@code done} 由本服务补写
 * {@code messageId} 并落库（assistant 消息 + 引用 JSON）；Python 的 {@code error}
 * 或调用失败统一转 {@code error} 帧，不写入半截回答。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LangchainChatService {

    /** 平台统一使用 Jackson 2 静态实例（Spring Boot 4 自动配置仅提供 Jackson 3 Bean） */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** SSE 超时在引擎超时基础上预留的余量（毫秒） */
    private static final long EMITTER_EXTRA_MILLIS = 30_000L;

    private final AgentConversationService conversationService;

    private final LangchainStreamClient streamClient;

    private final AgentProperties properties;

    /** 问答线程池：与 legacy 引擎一致，使用虚拟线程避免占用 Tomcat 请求线程 */
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * 发起 LangChain 引擎提问（SSE 流式返回）。
     *
     * @param request         提问请求
     * @param userId          当前用户
     * @param permissionCodes 网关透传的权限码
     * @return SSE 发射器
     */
    public SseEmitter chat(ChatRequest request, Long userId, String permissionCodes) {
        String question = request.getQuestion().trim();
        String scope = AgentConversationService.normalizeScope(request.getScope());
        AgentConversation conversation = conversationService.resolve(
                request.getConversationId(), userId, question, scope);
        long timeout = (properties.getQa().getLangchain().getTimeoutSeconds() * 1000L) + EMITTER_EXTRA_MILLIS;
        SseEmitter emitter = new SseEmitter(timeout);
        executor.submit(() -> answer(conversation, question, scope, userId, permissionCodes, emitter));
        return emitter;
    }

    /**
     * 执行一次问答（异步线程）。
     *
     * @param conversation    会话
     * @param question        问题
     * @param scope           检索范围
     * @param userId          提问者ID
     * @param permissionCodes 权限码
     * @param emitter         SSE 发射器
     */
    private void answer(AgentConversation conversation, String question, String scope,
                        Long userId, String permissionCodes, SseEmitter emitter) {
        long start = System.currentTimeMillis();
        RunState state = new RunState();
        conversationService.append(conversation.getId(), AgentConversationService.ROLE_USER, question, null, 0);
        try {
            streamClient.stream(requestBody(conversation.getId(), question, scope), userId, permissionCodes,
                    (event, data) -> relay(emitter, state, event, data));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            state.errorMessage = "智能体引擎调用被中断，请稍后重试";
        } catch (IOException | RuntimeException e) {
            log.warn("whale-ai 流式调用失败：{}", e.getMessage());
            state.errorMessage = "智能体引擎暂时不可用，请稍后重试";
        }
        if (state.errorMessage != null) {
            sendQuietly(emitter, "error", Map.of("message", state.errorMessage));
            emitter.complete();
            return;
        }
        if (!state.finished) {
            sendQuietly(emitter, "error", Map.of("message", "智能体引擎未返回完整回答，请稍后重试"));
            emitter.complete();
            return;
        }
        int tokens = state.tokens > 0 ? state.tokens
                : Math.max(1, (question.length() + state.answer.length()) / 2);
        AgentMessage message = conversationService.append(conversation.getId(),
                AgentConversationService.ROLE_ASSISTANT, state.answer.toString(),
                toJson(state.citations), tokens);
        conversationService.touch(conversation);
        sendQuietly(emitter, "done", ChatDoneVO.builder()
                .conversationId(String.valueOf(conversation.getId()))
                .messageId(String.valueOf(message.getId()))
                .tokens(tokens)
                .degraded(state.degraded)
                .elapsedMillis(System.currentTimeMillis() - start)
                .citations(state.citations)
                .build());
        emitter.complete();
    }

    /**
     * 分发 whale-ai 事件。
     *
     * @param emitter SSE 发射器
     * @param state   运行状态
     * @param event   事件名
     * @param data    原始 data
     */
    private void relay(SseEmitter emitter, RunState state, String event, String data) {
        switch (event) {
            case "delta" -> {
                String text = readText(data);
                if (StringUtils.hasText(text)) {
                    state.answer.append(text);
                    sendQuietly(emitter, "delta", Map.of("text", text));
                }
            }
            case "done" -> fillDone(state, data);
            case "error" -> state.errorMessage = readError(data);
            default -> sendRawQuietly(emitter, event, data);
        }
    }

    /**
     * 解析 done 事件（引用以 Python 校验结果为准）。
     *
     * @param state 运行状态
     * @param data  原始 data
     */
    private void fillDone(RunState state, String data) {
        try {
            JsonNode node = MAPPER.readTree(data);
            state.tokens = node.path("tokens").asInt(0);
            state.degraded = node.path("degraded").asBoolean(false);
            state.citations.addAll(readCitations(node.path("citations")));
            state.finished = true;
        } catch (JsonProcessingException e) {
            log.warn("whale-ai done 事件解析失败", e);
            state.errorMessage = "智能体回答解析失败，请稍后重试";
        }
    }

    /**
     * 解析引用数组。
     *
     * @param node 引用 JSON 数组
     * @return 引用视图对象
     */
    private List<CitationVO> readCitations(JsonNode node) {
        if (!node.isArray() || node.isEmpty()) {
            return List.of();
        }
        CitationVO[] values = MAPPER.convertValue(node, CitationVO[].class);
        return new ArrayList<>(List.of(values));
    }

    /**
     * 读取 delta 文本。
     *
     * @param data 原始 data
     * @return 文本（解析失败返回空串）
     */
    private static String readText(String data) {
        try {
            return MAPPER.readTree(data).path("text").asText("");
        } catch (JsonProcessingException e) {
            return "";
        }
    }

    /**
     * 读取 error 消息。
     *
     * @param data 原始 data
     * @return 错误消息
     */
    private static String readError(String data) {
        try {
            String message = MAPPER.readTree(data).path("message").asText("");
            return StringUtils.hasText(message) ? message : "智能体引擎执行失败，请稍后重试";
        } catch (JsonProcessingException e) {
            return "智能体引擎执行失败，请稍后重试";
        }
    }

    /**
     * 构造请求体。
     *
     * @param conversationId 会话ID
     * @param question       问题
     * @param scope          检索范围
     * @return JSON 字符串
     */
    private static String requestBody(Long conversationId, String question, String scope) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("conversationId", conversationId);
        body.put("question", question);
        if (StringUtils.hasText(scope)) {
            body.put("scope", scope);
        }
        try {
            return MAPPER.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("请求体序列化失败", e);
        }
    }

    /**
     * 引用序列化为 JSON。
     *
     * @param citations 引用
     * @return JSON 字符串
     */
    private static String toJson(List<CitationVO> citations) {
        try {
            return MAPPER.writeValueAsString(citations);
        } catch (JsonProcessingException e) {
            log.warn("引用序列化失败", e);
            return "[]";
        }
    }

    /**
     * 发送 SSE 事件。
     *
     * @param emitter SSE 发射器
     * @param event   事件名
     * @param data    事件数据
     * @throws IOException 写入失败
     */
    private static void send(SseEmitter emitter, String event, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(event).data(MAPPER.writeValueAsString(data)));
    }

    /**
     * 原样转发一帧（保留 Python 载荷结构）。
     *
     * @param emitter SSE 发射器
     * @param event   事件名
     * @param data    原始 JSON
     * @throws IOException 写入失败
     */
    private static void sendRaw(SseEmitter emitter, String event, String data) throws IOException {
        emitter.send(SseEmitter.event().name(event).data(data));
    }

    /**
     * 发送事件（忽略客户端断开）。
     *
     * @param emitter SSE 发射器
     * @param event   事件名
     * @param data    事件数据
     */
    private static void sendQuietly(SseEmitter emitter, String event, Object data) {
        try {
            send(emitter, event, data);
        } catch (IOException e) {
            log.debug("SSE 写入失败（客户端可能已断开）：event={}", event);
        }
    }

    /**
     * 原样转发（忽略客户端断开）。
     *
     * @param emitter SSE 发射器
     * @param event   事件名
     * @param data    原始 JSON
     */
    private static void sendRawQuietly(SseEmitter emitter, String event, String data) {
        try {
            sendRaw(emitter, event, data);
        } catch (IOException e) {
            log.debug("SSE 写入失败（客户端可能已断开）：event={}", event);
        }
    }

    /**
     * 关闭问答线程池。
     */
    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }

    /**
     * 单次运行状态（供事件回调累积）。
     *
     * @author BlueWhale
     */
    private static final class RunState {

        /** 回答缓冲 */
        private final StringBuilder answer = new StringBuilder();

        /** 引用（已由 Python 校验） */
        private final List<CitationVO> citations = new ArrayList<>();

        /** token 数 */
        private int tokens;

        /** 是否降级 */
        private boolean degraded;

        /** 是否已收到 done */
        private boolean finished;

        /** 错误消息（空表示无错误） */
        private String errorMessage;
    }
}
