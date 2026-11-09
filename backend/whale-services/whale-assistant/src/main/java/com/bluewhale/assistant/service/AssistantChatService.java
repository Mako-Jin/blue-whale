package com.bluewhale.assistant.service;

import com.bluewhale.assistant.config.AssistantProperties;
import com.bluewhale.assistant.dto.ChatDoneVO;
import com.bluewhale.assistant.dto.CitationVO;
import com.bluewhale.assistant.dto.ChatRequest;
import com.bluewhale.assistant.dto.OpenChatRequest;
import com.bluewhale.assistant.dto.OpenChatVO;
import com.bluewhale.assistant.dto.SessionCreateRequest;
import com.bluewhale.assistant.entity.AsstDefinition;
import com.bluewhale.assistant.entity.AsstMessage;
import com.bluewhale.assistant.entity.AsstSession;
import com.bluewhale.assistant.support.AssistantStreamClient;
import com.bluewhale.common.core.exception.BizException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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
 * 助手问答服务：把 {@code /assistant/sessions/{id}/stream} 委托给 ai/whale-ai，SSE 契约对齐 whale-agent。
 *
 * <p>事件映射：Python 的 {@code meta/tool/delta} 原样透传；{@code done} 由本服务补写
 * {@code messageId} 并落库（assistant 消息 + 引用 JSON + 用量聚合）；Python 的 {@code error}
 * 或调用失败统一转 {@code error} 帧，不写入半截回答。</p>
 *
 * <p>助手配置（systemPrompt/modelParams/toolWhitelist）由 {@code asst_definition} 解析后
 * 随请求下发 whale-ai；本服务不持模型凭据。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantChatService {

    /** 平台统一使用 Jackson 2 静态实例（Spring Boot 4 自动配置仅提供 Jackson 3 Bean） */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** SSE 超时在引擎超时基础上预留的余量（毫秒） */
    private static final long EMITTER_EXTRA_MILLIS = 30_000L;

    private final AsstSessionService sessionService;

    private final AsstDefinitionService definitionService;

    private final AsstUsageService usageService;

    private final AssistantStreamClient streamClient;

    private final AssistantProperties properties;

    /** 问答线程池：虚拟线程，避免占用 Tomcat 请求线程（与 whale-agent 一致） */
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * 发起助手问答（SSE 流式返回）。
     *
     * @param sessionId       会话ID
     * @param request         提问请求
     * @param userId          当前用户
     * @param permissionCodes 网关透传的权限码
     * @return SSE 发射器
     */
    public SseEmitter chat(Long sessionId, ChatRequest request, Long userId, String permissionCodes) {
        AsstSession session = sessionService.requireOwned(sessionId, userId);
        AsstDefinition definition = definitionService.requirePublished(session.getAssistantId());
        String question = request.getQuestion().trim();
        long timeout = (properties.getAi().getTimeoutSeconds() * 1000L) + EMITTER_EXTRA_MILLIS;
        SseEmitter emitter = new SseEmitter(timeout);
        executor.submit(() -> answer(session, definition, question, userId, permissionCodes, emitter));
        return emitter;
    }

    /**
     * 开放 API 问答（同步聚合 whale-ai SSE，返回最终回答）。
     *
     * <p>会话来源固定为 {@code openapi}；传入 {@code sessionId} 时续聊既有会话
     * （须归属当前用户且属于同一助手），否则自动新建会话。回答与引用照常落库。</p>
     *
     * @param request         开放问答请求
     * @param userId          业务侧透传的最终用户ID
     * @param permissionCodes 业务侧可选透传的权限码
     * @return 聚合后的回答结果
     */
    public OpenChatVO chatSync(OpenChatRequest request, Long userId, String permissionCodes) {
        AsstDefinition definition = definitionService.requirePublishedByCode(request.getAssistantCode());
        AsstSession session;
        if (request.getSessionId() != null) {
            session = sessionService.requireOwned(request.getSessionId(), userId);
            if (!definition.getId().equals(session.getAssistantId())) {
                throw new BizException("会话与助手不匹配，无法续聊");
            }
        } else {
            SessionCreateRequest createRequest = new SessionCreateRequest();
            createRequest.setAssistantId(definition.getId());
            createRequest.setSource(AsstSessionService.SOURCE_OPENAPI);
            createRequest.setChannelRef(request.getChannelRef());
            session = sessionService.createSession(createRequest, userId);
        }
        ChatOutcome outcome = execute(session, definition, request.getQuestion().trim(), userId, permissionCodes, null);
        if (outcome.errorMessage() != null) {
            throw new BizException(outcome.errorMessage());
        }
        return OpenChatVO.builder()
                .sessionId(String.valueOf(session.getId()))
                .assistantId(String.valueOf(definition.getId()))
                .messageId(String.valueOf(outcome.message().getId()))
                .answer(outcome.answer())
                .citations(outcome.citations())
                .tokens(outcome.tokens())
                .degraded(outcome.degraded())
                .elapsedMillis(outcome.elapsedMillis())
                .build();
    }

    /**
     * 执行一次问答并落库（异步 SSE 线程或开放 API 同步线程）。
     *
     * <p>事件累积由 {@link #accumulate} 统一完成；{@code sink} 为 SSE 逐帧转发回调
     * （开放 API 传 {@code null}）。用户消息与助手消息、引用、用量聚合均在本方法内落库。</p>
     *
     * @param session         会话
     * @param definition      已发布助手定义
     * @param question        问题
     * @param userId          提问者ID
     * @param permissionCodes 权限码
     * @param sink            SSE 转发回调（可空）
     * @return 运行结果（含错误消息或最终回答）
     */
    private ChatOutcome execute(AsstSession session, AsstDefinition definition, String question,
                                Long userId, String permissionCodes, FrameSink sink) {
        long start = System.currentTimeMillis();
        RunState state = new RunState();
        List<String> whitelist = definitionService.toolWhitelist(definition.getId());
        try {
            sessionService.append(session.getId(),
                    AsstSessionService.ROLE_USER, question, null, 0, 0, 0);
            if ("新会话".equals(session.getTitle())) {
                session.setTitle(AsstSessionService.titleOf(question));
            }
            streamClient.stream(requestBody(session, definition, question, whitelist), userId, permissionCodes,
                    (event, data) -> {
                        accumulate(state, event, data);
                        if (sink != null) {
                            sink.accept(event, data);
                        }
                    });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            state.errorMessage = "智能体引擎调用被中断，请稍后重试";
        } catch (BizException e) {
            // 提问落库失败（消息超限/正文超长等）：原因原样透传
            state.errorMessage = e.getMessage();
        } catch (IOException | RuntimeException e) {
            log.warn("whale-ai 流式调用失败：{}", e.getMessage());
            state.errorMessage = "智能体引擎暂时不可用，请稍后重试";
        }
        if (state.errorMessage != null) {
            return ChatOutcome.failed(state.errorMessage);
        }
        if (!state.finished) {
            return ChatOutcome.failed("智能体引擎未返回完整回答，请稍后重试");
        }
        int tokens = state.tokens > 0 ? state.tokens
                : Math.max(1, (question.length() + state.answer.length()) / 2);
        int elapsedMs = (int) (System.currentTimeMillis() - start);
        AsstMessage message = sessionService.append(session.getId(),
                AsstSessionService.ROLE_ASSISTANT, state.answer.toString(),
                toJson(state.citations), tokens, elapsedMs, state.degraded ? 1 : 0);
        sessionService.touch(session);
        usageService.recordAnswer(definition.getId(), tokens);
        return new ChatOutcome(null, state.answer.toString(), state.citations,
                tokens, elapsedMs, state.degraded, message);
    }
    /**
     * SSE 路径：执行问答并逐帧下发（含最终 done 帧）。
     *
     * @param session         会话
     * @param definition      已发布助手定义
     * @param question        问题
     * @param userId          提问者ID
     * @param permissionCodes 权限码
     * @param emitter         SSE 发射器
     */
    private void answer(AsstSession session, AsstDefinition definition, String question,
                        Long userId, String permissionCodes, SseEmitter emitter) {
        RunState state = new RunState();
        ChatOutcome outcome = execute(session, definition, question, userId, permissionCodes,
                (event, data) -> relay(emitter, event, data));
        if (outcome.errorMessage() != null) {
            sendQuietly(emitter, "error", Map.of("message", outcome.errorMessage()));
        } else {
            sendQuietly(emitter, "done", ChatDoneVO.builder()
                    .sessionId(String.valueOf(session.getId()))
                    .assistantId(String.valueOf(definition.getId()))
                    .messageId(String.valueOf(outcome.message().getId()))
                    .tokens(outcome.tokens())
                    .degraded(outcome.degraded())
                    .elapsedMillis(outcome.elapsedMillis())
                    .citations(outcome.citations())
                    .build());
        }
        emitter.complete();
    }

    /**
     * 累积 whale-ai 事件到运行状态（与转发解耦，SSE/开放 API 共用）。
     *
     * @param state 运行状态
     * @param event 事件名
     * @param data  原始 data
     */
    private void accumulate(RunState state, String event, String data) {
        switch (event) {
            case "delta" -> {
                String text = readText(data);
                if (StringUtils.hasText(text)) {
                    state.answer.append(text);
                }
            }
            case "done" -> fillDone(state, data);
            case "error" -> state.errorMessage = readError(data);
            default -> {
                // meta/tool 等事件不参与回答累积
            }
        }
    }

    /**
     * 向 SSE 转发 whale-ai 事件（done/error 由 execute 收敛后统一处理，不在此转发）。
     *
     * @param emitter SSE 发射器
     * @param event   事件名
     * @param data    原始 data
     */
    private void relay(SseEmitter emitter, String event, String data) {
        switch (event) {
            case "delta" -> {
                String text = readText(data);
                if (StringUtils.hasText(text)) {
                    sendQuietly(emitter, "delta", Map.of("text", text));
                }
            }
            case "done", "error" -> {
                // 由 execute 收敛后在 answer() 统一下发
            }
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
     * 构造 whale-ai 请求体（含助手配置下发）。
     *
     * @param session    会话
     * @param definition 已发布助手定义
     * @param question   问题
     * @param whitelist  已授权工具编码（来自 asst_tool_grant）
     * @return JSON 字符串
     */
    private static String requestBody(AsstSession session, AsstDefinition definition, String question,
                                      List<String> whitelist) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("conversationId", session.getId());
        body.put("question", question);
        if (StringUtils.hasText(definition.getKnowledgeScope())) {
            body.put("scope", definition.getKnowledgeScope());
        }
        Map<String, Object> assistant = new LinkedHashMap<>();
        assistant.put("assistantId", definition.getCode());
        if (StringUtils.hasText(definition.getSystemPrompt())) {
            assistant.put("systemPrompt", definition.getSystemPrompt());
        }
        Map<String, Object> modelParams = parseJsonMap(definition.getModelParamsJson());
        if (modelParams != null && !modelParams.isEmpty()) {
            assistant.put("modelParams", modelParams);
        }
        if (whitelist != null && !whitelist.isEmpty()) {
            assistant.put("toolWhitelist", whitelist);
        }
        body.put("assistant", assistant);
        try {
            return MAPPER.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("请求体序列化失败", e);
        }
    }

    /**
     * 解析模型参数 JSON。
     *
     * @param json JSON 串
     * @return 参数 Map；空返回 {@code null}
     */
    private static Map<String, Object> parseJsonMap(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            log.warn("模型参数 JSON 解析失败", e);
            return null;
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
     * SSE 逐帧转发回调（事件名, 原始 data）。
     *
     * @author BlueWhale
     */
    @FunctionalInterface
    private interface FrameSink {

        /**
         * 接收一帧。
         *
         * @param event 事件名
         * @param data  原始 data
         */
        void accept(String event, String data);
    }

    /**
     * 单次问答运行结果（错误消息或最终回答，消息与用量已落库）。
     *
     * @param errorMessage 错误消息（{@code null} 表示成功）
     * @param answer       回答正文
     * @param citations    引用清单
     * @param tokens         token 统计
     * @param elapsedMillis  耗时（毫秒）
     * @param degraded       是否降级
     * @param message        落库的助手消息（失败时为 {@code null}）
     * @author BlueWhale
     */
    private record ChatOutcome(String errorMessage, String answer, List<CitationVO> citations,
                               int tokens, int elapsedMillis, boolean degraded, AsstMessage message) {

        /**
         * 构造失败结果。
         *
         * @param message 错误消息
         * @return 失败结果
         */
        static ChatOutcome failed(String message) {
            return new ChatOutcome(message, "", List.of(), 0, 0, false, null);
        }
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
