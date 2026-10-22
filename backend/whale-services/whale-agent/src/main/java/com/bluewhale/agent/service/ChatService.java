package com.bluewhale.agent.service;

import com.bluewhale.agent.config.AgentProperties;
import com.bluewhale.agent.dto.ChatDoneVO;
import com.bluewhale.agent.dto.ChatMetaVO;
import com.bluewhale.agent.dto.ChatRequest;
import com.bluewhale.agent.dto.CitationVO;
import com.bluewhale.agent.entity.AgentConversation;
import com.bluewhale.agent.entity.AgentFact;
import com.bluewhale.agent.entity.AgentMessage;
import com.bluewhale.agent.support.index.AgentIndexService;
import com.bluewhale.agent.support.index.SearchHit;
import com.bluewhale.agent.support.llm.ChatMessage;
import com.bluewhale.agent.support.llm.LlmClient;
import com.bluewhale.agent.support.qa.Citation;
import com.bluewhale.agent.support.qa.CitationValidator;
import com.bluewhale.agent.support.qa.FactRetriever;
import com.bluewhale.agent.support.qa.IntentRouter;
import com.bluewhale.agent.support.qa.PromptAssembler;
import com.bluewhale.agent.support.qa.QuestionIntent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 问答编排：意图路由 → 事实 + 全文证据召回 → 提示词组装 → 流式生成 → 引用校验 → 会话落库。
 *
 * <p>降级链：未配置模型（{@code whale.agent.llm.api-key}）时直接返回事实/检索摘要；
 * 模型流式调用失败且尚无增量时同样回落摘要；索引为空时在摘要中提示重建入口。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    /** 平台统一使用 Jackson 2 静态实例（Spring Boot 4 自动配置仅提供 Jackson 3 Bean） */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** SSE 连接超时（毫秒） */
    private static final long EMITTER_TIMEOUT_MILLIS = 180_000L;

    /** 全文索引支持的范围类型 */
    private static final Set<String> INDEX_KINDS = Set.of("code", "doc", "sql", "config");

    /** 事实片段最大字符数 */
    private static final int FACT_SNIPPET_LIMIT = 600;

    /** 摘要中单条证据最大字符数 */
    private static final int SUMMARY_SNIPPET_LIMIT = 200;

    /** 单条历史消息最大字符数（控制提示词体积） */
    private static final int HISTORY_MESSAGE_LIMIT = 1500;

    private final AgentConversationService conversationService;

    private final FactRetriever factRetriever;

    private final AgentSearchService searchService;

    private final AgentIndexService indexService;

    private final IntentRouter intentRouter;

    private final PromptAssembler promptAssembler;

    private final CitationValidator citationValidator;

    private final LlmClient llmClient;

    private final AgentProperties properties;

    /** 问答线程池：每次提问一个虚拟线程，避免阻塞 Tomcat 请求线程 */
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * 发起提问（SSE 流式返回）。
     *
     * <p>会话归属与参数校验在调用线程内同步完成，校验失败直接抛出业务异常（统一 JSON 错误响应）；
     * 生成过程在虚拟线程中执行，事件依次为 {@code meta → delta* → done|error}。</p>
     *
     * @param request        提问请求
     * @param userId         当前用户
     * @param includeRuntime 是否包含运行时事实（取决于 {@code sys:agent:runtime:read} 权限）
     * @return SSE 发射器
     */
    public SseEmitter chat(ChatRequest request, Long userId, boolean includeRuntime) {
        String question = request.getQuestion().trim();
        String scope = AgentConversationService.normalizeScope(request.getScope());
        AgentConversation conversation = conversationService.resolve(
                request.getConversationId(), userId, question, scope);
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MILLIS);
        executor.submit(() -> answer(conversation, question, scope, includeRuntime, emitter));
        return emitter;
    }

    /**
     * 执行一次问答（异步线程）。
     *
     * @param conversation   会话
     * @param question       问题
     * @param scope          检索范围
     * @param includeRuntime 是否包含运行时事实
     * @param emitter        SSE 发射器
     */
    private void answer(AgentConversation conversation, String question, String scope,
                        boolean includeRuntime, SseEmitter emitter) {
        long start = System.currentTimeMillis();
        try {
            QuestionIntent intent = intentRouter.route(question);
            List<ChatMessage> history = history(conversation.getId());
            List<Citation> citations = collectCitations(question, scope, intent, includeRuntime);
            conversationService.append(conversation.getId(), AgentConversationService.ROLE_USER,
                    question, null, 0);

            boolean degraded = !llmClient.available();
            send(emitter, "meta", ChatMetaVO.builder()
                    .conversationId(String.valueOf(conversation.getId()))
                    .intent(intent.name())
                    .degraded(degraded)
                    .citations(toVOs(citations))
                    .build());

            StringBuilder answer = new StringBuilder();
            if (degraded) {
                answer.append(summary(question, citations));
                send(emitter, "delta", Map.of("text", answer.toString()));
            } else {
                degraded = stream(question, citations, history, answer, emitter);
            }
            if (answer.length() == 0) {
                answer.append(emptyAnswer(question, citations));
                send(emitter, "delta", Map.of("text", answer.toString()));
            }

            int tokens = estimateTokens(history, question, answer.length());
            String citationsJson = toJson(citations);
            AgentMessage message = conversationService.append(conversation.getId(),
                    AgentConversationService.ROLE_ASSISTANT, answer.toString(), citationsJson, tokens);
            conversationService.touch(conversation);

            send(emitter, "done", ChatDoneVO.builder()
                    .conversationId(String.valueOf(conversation.getId()))
                    .messageId(String.valueOf(message.getId()))
                    .tokens(tokens)
                    .degraded(degraded)
                    .elapsedMillis(System.currentTimeMillis() - start)
                    .citations(toVOs(citations))
                    .build());
            emitter.complete();
        } catch (Exception e) {
            log.error("问答执行失败：conversationId={}", conversation.getId(), e);
            sendQuietly(emitter, "error", Map.of("message", "问答执行失败：" + safeMessage(e)));
            emitter.complete();
        }
    }

    /**
     * 模型流式生成；失败且无任何增量时回落检索摘要。
     *
     * @param question  问题
     * @param citations 证据
     * @param history   历史消息
     * @param answer    回答缓冲
     * @param emitter   SSE 发射器
     * @return 最终是否处于降级状态
     */
    private boolean stream(String question, List<Citation> citations, List<ChatMessage> history,
                           StringBuilder answer, SseEmitter emitter) {
        List<ChatMessage> messages = promptAssembler.assemble(question, citations, history);
        try {
            llmClient.stream(messages, delta -> {
                answer.append(delta);
                sendQuietly(emitter, "delta", Map.of("text", delta));
            });
            return false;
        } catch (IOException e) {
            log.warn("模型流式回答失败：{}", e.getMessage());
            if (answer.length() == 0) {
                answer.append(summary(question, citations));
                sendQuietly(emitter, "delta", Map.of("text", answer.toString()));
                return true;
            }
            return false;
        }
    }

    /**
     * 召回证据：结构化事实 + BM25 全文命中，编号从 S1 起连续分配。
     *
     * @param question       问题
     * @param scope          检索范围
     * @param intent         意图
     * @param includeRuntime 是否包含运行时事实
     * @return 通过校验的引用
     */
    private List<Citation> collectCitations(String question, String scope, QuestionIntent intent,
                                            boolean includeRuntime) {
        List<Citation> citations = new ArrayList<>();
        int seq = 1;
        if (intent != QuestionIntent.SEMANTIC) {
            for (AgentFact fact : factRetriever.retrieve(question, includeRuntime)) {
                citations.add(new Citation("S" + seq++, "fact", fact.getSourcePath(), fact.getFactKey(),
                        null, null, truncate(fact.getValueJson(), FACT_SNIPPET_LIMIT),
                        fact.getFactType(), fact.getFactKey(), null));
            }
        }
        Set<String> kinds = indexKinds(scope);
        boolean needChunks = intent != QuestionIntent.STRUCTURED || citations.isEmpty();
        if (needChunks && (scope == null || !kinds.isEmpty())) {
            List<SearchHit> hits = searchService.search(question,
                    properties.getQa().getEvidenceTopK(), kinds);
            int maxChars = properties.getQa().getMaxChunkChars();
            for (SearchHit hit : hits) {
                citations.add(new Citation("S" + seq++, "chunk", hit.getPath(), hit.getTitle(),
                        hit.getStartLine(), hit.getEndLine(),
                        truncate(hit.getContent(), maxChars), null, null, (double) hit.getScore()));
            }
        }
        return citationValidator.validate(citations);
    }

    /**
     * 将检索范围映射为索引类型过滤。
     *
     * @param scope 检索范围
     * @return 类型集合；无范围限制返回空集合
     */
    private static Set<String> indexKinds(String scope) {
        if (!StringUtils.hasText(scope)) {
            return Set.of();
        }
        Set<String> kinds = new LinkedHashSet<>();
        for (String part : scope.split(",")) {
            String value = part.trim();
            if (INDEX_KINDS.contains(value)) {
                kinds.add(value);
            }
        }
        return kinds;
    }

    /**
     * 读取历史消息并转为模型消息。
     *
     * @param conversationId 会话ID
     * @return 历史消息（user / assistant）
     */
    private List<ChatMessage> history(Long conversationId) {
        List<AgentMessage> recent = conversationService.recent(
                conversationId, properties.getQa().getHistoryMessages());
        List<ChatMessage> history = new ArrayList<>(recent.size());
        for (AgentMessage message : recent) {
            if (!StringUtils.hasText(message.getContent())) {
                continue;
            }
            String content = truncate(message.getContent(), HISTORY_MESSAGE_LIMIT);
            history.add(AgentConversationService.ROLE_ASSISTANT.equals(message.getRole())
                    ? ChatMessage.assistant(content) : ChatMessage.user(content));
        }
        return history;
    }

    /**
     * 无模型时的检索摘要。
     *
     * @param question  问题
     * @param citations 证据
     * @return Markdown 摘要
     */
    private String summary(String question, List<Citation> citations) {
        if (citations.isEmpty()) {
            return emptyAnswer(question, citations);
        }
        StringBuilder builder = new StringBuilder("模型未接入，以下为检索结果摘要（共 ")
                .append(citations.size()).append(" 条证据）：\n\n");
        List<Citation> facts = citations.stream().filter(item -> "fact".equals(item.kind())).toList();
        List<Citation> chunks = citations.stream().filter(item -> "chunk".equals(item.kind())).toList();
        if (!facts.isEmpty()) {
            builder.append("### 结构化事实\n");
            for (Citation fact : facts) {
                builder.append("- [").append(fact.sourceId()).append("] `")
                        .append(fact.factType()).append(' ').append(fact.factKey()).append("`：")
                        .append(oneLine(fact.snippet(), SUMMARY_SNIPPET_LIMIT))
                        .append("（来源：").append(fact.path()).append("）\n");
            }
            builder.append('\n');
        }
        if (!chunks.isEmpty()) {
            builder.append("### 全文证据\n");
            for (Citation chunk : chunks) {
                builder.append("- [").append(chunk.sourceId()).append("] `").append(chunk.path()).append('`');
                if (chunk.startLine() != null) {
                    builder.append("（L").append(chunk.startLine()).append('-')
                            .append(chunk.endLine()).append("）");
                }
                builder.append('：').append(oneLine(chunk.snippet(), SUMMARY_SNIPPET_LIMIT)).append('\n');
            }
            builder.append('\n');
        }
        builder.append("> 配置 `whale.agent.llm.api-key` 后可获得模型生成的完整回答。");
        return builder.toString();
    }

    /**
     * 无证据时的固定回答（含索引状态提示）。
     *
     * @param question  问题
     * @param citations 证据
     * @return Markdown 提示
     */
    private String emptyAnswer(String question, List<Citation> citations) {
        if (!citations.isEmpty()) {
            return "模型未返回内容，请参考右侧证据面板核对资料。";
        }
        return "知识库中未找到与「" + oneLine(question, 120) + "」相关的资料。\n\n"
                + "可尝试：补充服务名、表名或接口路径等关键词；或先重建索引"
                + "（`POST /agent/index/rebuild`，当前索引状态：" + indexService.status() + "）。";
    }

    /**
     * 估算 token（中文按 1 字 ≈ 0.5 token 粗估）。
     *
     * @param history      历史消息
     * @param question     问题
     * @param answerLength 回答字符数
     * @return 估算 token
     */
    private static int estimateTokens(List<ChatMessage> history, String question, int answerLength) {
        int chars = question.length() + answerLength;
        for (ChatMessage message : history) {
            chars += message.content().length();
        }
        return Math.max(1, chars / 2);
    }

    /**
     * 引用转视图对象。
     *
     * @param citations 引用
     * @return 视图对象列表
     */
    private static List<CitationVO> toVOs(List<Citation> citations) {
        List<CitationVO> result = new ArrayList<>(citations.size());
        for (Citation citation : citations) {
            result.add(CitationVO.of(citation));
        }
        return result;
    }

    /**
     * 引用序列化为 JSON。
     *
     * @param citations 引用
     * @return JSON 字符串
     */
    private static String toJson(List<Citation> citations) {
        try {
            return MAPPER.writeValueAsString(toVOs(citations));
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
     * 发送 SSE 事件（忽略客户端断开）。
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
     * 截断文本。
     *
     * @param text  文本
     * @param limit 上限字符数
     * @return 截断结果
     */
    private static String truncate(String text, int limit) {
        if (text == null) {
            return "";
        }
        return text.length() <= limit ? text : text.substring(0, limit) + "…";
    }

    /**
     * 压成单行并截断。
     *
     * @param text  文本
     * @param limit 上限字符数
     * @return 单行文本
     */
    private static String oneLine(String text, int limit) {
        String flat = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        return flat.length() <= limit ? flat : flat.substring(0, limit) + "…";
    }

    /**
     * 安全读取异常消息。
     *
     * @param e 异常
     * @return 消息文本
     */
    private static String safeMessage(Exception e) {
        return StringUtils.hasText(e.getMessage()) ? e.getMessage() : e.getClass().getSimpleName();
    }

    /**
     * 关闭问答线程池。
     */
    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }
}
