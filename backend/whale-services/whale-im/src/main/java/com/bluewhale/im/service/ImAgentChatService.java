package com.bluewhale.im.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bluewhale.im.entity.ImAgentBinding;
import com.bluewhale.im.entity.ImAgentRun;
import com.bluewhale.im.entity.ImConversation;
import com.bluewhale.im.entity.ImMessage;
import com.bluewhale.im.mapper.ImAgentBindingMapper;
import com.bluewhale.im.mapper.ImAgentRunMapper;
import com.bluewhale.im.mapper.ImConversationMapper;
import com.bluewhale.im.mapper.ImMessageMapper;
import com.bluewhale.im.support.AgentStreamClient;
import com.bluewhale.im.support.AgentTraceMasker;
import com.bluewhale.im.ws.ImMessageBroadcaster;
import com.bluewhale.im.ws.WsFrames;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 机器人回答编排：机器人会话收到用户消息后，调用 {@code whale-agent} 的 SSE 流式接口，
 * 把 {@code meta/delta/tool/done/error} 转换为 WS 帧并落库机器人回复。
 *
 * <p>关键约束：流式调用在独立线程执行，不阻塞消息发送请求；回答以 {@code sender_type=BOT}
 * 落库（失败时为 {@code SYSTEM} 提示），数据库始终为唯一事实源；超时/异常一律降级为
 * 提示消息 + {@code agent.error} 帧，不影响会话继续使用。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
public class ImAgentChatService {

    /** 会话类型：机器人会话 */
    private static final String TYPE_BOT = "BOT";

    /** 触发去重键前缀（同一条消息只触发一次） */
    private static final String TRIGGER_KEY_PREFIX = "whale:im:agent:trigger:";

    /** 触发去重键有效期（小时） */
    private static final long TRIGGER_TTL_HOURS = 24L;

    /** 提问限流键前缀（按用户 + 分钟窗口） */
    private static final String RATE_KEY_PREFIX = "whale:im:agent:rate:";

    /** 限流键有效期（秒），略大于窗口便于自然过期 */
    private static final long RATE_TTL_SECONDS = 90L;

    /** 限流提示文案 */
    private static final String RATE_LIMIT_HINT = "提问过于频繁，请稍后再试";

    /** 每分钟毫秒数（限流窗口） */
    private static final long MILLIS_PER_MINUTE = 60_000L;

    /** 发送者类型：机器人 */
    private static final String SENDER_BOT = "BOT";

    /** 发送者类型：系统 */
    private static final String SENDER_SYSTEM = "SYSTEM";

    /** 内容类型：Markdown */
    private static final String CONTENT_MARKDOWN = "MARKDOWN";

    /** 内容类型：纯文本 */
    private static final String CONTENT_TEXT = "TEXT";

    /** 内容类型：确认卡片 */
    private static final String CONTENT_CARD = "CARD";

    /** 运行状态：运行中 */
    private static final String STATUS_RUNNING = "RUNNING";

    /** 运行状态：等待确认 */
    private static final String STATUS_WAITING_CONFIRM = "WAITING_CONFIRM";

    /** 运行状态：成功 */
    private static final String STATUS_SUCCESS = "SUCCESS";

    /** 运行状态：失败 */
    private static final String STATUS_FAILED = "FAILED";

    /** 降级提示文案 */
    private static final String DEGRADED_HINT = "智能体暂时不可用，请稍后重试";

    /** 中断提示文案 */
    private static final String INTERRUPTED_HINT = "回答已中断，请稍后重试";

    /** 问题最大长度（与 whale-agent {@code ChatRequest} 对齐） */
    private static final int MAX_QUESTION_LENGTH = 2000;

    /** 消息序号重试次数 */
    private static final int MAX_SEQ_ATTEMPTS = 3;

    /** 绑定启用 */
    private static final int BINDING_ENABLED = 1;

    private final ImAgentBindingMapper bindingMapper;

    private final ImAgentRunMapper runMapper;

    private final ImConversationMapper conversationMapper;

    private final ImMessageMapper messageMapper;

    private final MessageSeqAllocator seqAllocator;

    private final ImMessageBroadcaster broadcaster;

    private final AgentStreamClient agentStreamClient;

    private final ImBotService botService;

    private final StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper;

    private final ThreadPoolTaskExecutor executor;

    /** 单用户每分钟提问上限 */
    private final int rateLimitPerMinute;

    /**
     * 构造机器人回答编排服务。
     *
     * @param bindingMapper     绑定 Mapper
     * @param runMapper         运行记录 Mapper
     * @param conversationMapper 会话 Mapper
     * @param messageMapper     消息 Mapper
     * @param seqAllocator      序号分配器
     * @param broadcaster       广播器
     * @param agentStreamClient 智能体流式客户端
     * @param botService        机器人会话服务（提供机器人ID）
     * @param redisTemplate     Redis 模板（触发去重/限流）
     * @param objectMapper      JSON 序列化器
     * @param executor          流式调用线程池
     * @param rateLimitPerMinute 单用户每分钟提问上限
     */
    public ImAgentChatService(ImAgentBindingMapper bindingMapper, ImAgentRunMapper runMapper,
                              ImConversationMapper conversationMapper, ImMessageMapper messageMapper,
                              MessageSeqAllocator seqAllocator, ImMessageBroadcaster broadcaster,
                              AgentStreamClient agentStreamClient, ImBotService botService,
                              StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
                              @Qualifier("imAgentExecutor") ThreadPoolTaskExecutor executor,
                              @Value("${whale.im.agent.rate-limit-per-minute:20}") int rateLimitPerMinute) {
        this.bindingMapper = bindingMapper;
        this.runMapper = runMapper;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.seqAllocator = seqAllocator;
        this.broadcaster = broadcaster;
        this.agentStreamClient = agentStreamClient;
        this.botService = botService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.executor = executor;
        this.rateLimitPerMinute = Math.max(1, rateLimitPerMinute);
    }

    /**
     * 用户消息落库后触发机器人回答。
     *
     * <p>触发口径：{@code BOT} 会话直接触发；{@code GROUP} 会话仅在内容包含
     * {@code @机器人名} 时触发，并在首次使用时补齐机器人成员与绑定。同一消息ID
     * 通过 Redis 原子占位去重，重复投递（HTTP 重试/并发重发）只触发一次。</p>
     *
     * <p>异步执行，立即返回；无绑定、非目标会话或内容为空时静默跳过。</p>
     *
     * @param message         已落库的用户消息
     * @param permissionCodes 当前用户权限码（透传智能体做权限校验）
     */
    public void onUserMessage(ImMessage message, String permissionCodes) {
        if (message == null || !ImMessageService.SENDER_USER.equals(message.getSenderType())) {
            return;
        }
        String content = message.getContent();
        if (!StringUtils.hasText(content)) {
            return;
        }
        ImConversation conversation = conversationMapper.selectById(message.getConversationId());
        if (conversation == null) {
            return;
        }
        ImAgentBinding binding = resolveBinding(conversation, content);
        if (binding == null) {
            return;
        }
        if (!claimTrigger(message.getId())) {
            log.debug("消息已触发过机器人，跳过重复投递: messageId={}", message.getId());
            return;
        }
        Long conversationId = conversation.getId();
        Long requestMessageId = message.getId();
        Long userId = message.getSenderId();
        String question = content;
        if (!allowWithinRateLimit(userId)) {
            notifyRateLimited(conversationId);
            return;
        }
        executor.execute(() -> execute(conversationId, requestMessageId, userId, question, binding, permissionCodes));
    }

    /**
     * 单用户分钟级限流：Redis 计数，超过上限拒绝本次触发。
     *
     * <p>Redis 不可用时放行（降级优先于限流），仅记录告警。</p>
     *
     * @param userId 提问用户ID
     * @return 允许本次请求返回 {@code true}
     */
    private boolean allowWithinRateLimit(Long userId) {
        if (userId == null) {
            return true;
        }
        String key = RATE_KEY_PREFIX + userId + ":" + (System.currentTimeMillis() / MILLIS_PER_MINUTE);
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, Duration.ofSeconds(RATE_TTL_SECONDS));
            }
            return count == null || count <= rateLimitPerMinute;
        } catch (RuntimeException e) {
            log.warn("提问限流计数失败，按放行处理: userId={}", userId, e);
            return true;
        }
    }

    /**
     * 限流提示：落一条 BOT 文本消息并广播，不创建运行记录。
     *
     * @param conversationId 会话ID
     */
    private void notifyRateLimited(Long conversationId) {
        ImMessage hint = appendMessage(conversationId, botService.botUserId(), SENDER_BOT,
                CONTENT_TEXT, RATE_LIMIT_HINT);
        broadcaster.broadcast(conversationId, ImMessageService.toVo(hint));
        log.info("提问触发限流: conversationId={}", conversationId);
    }

    /**
     * 解析消息对应的机器人绑定：BOT 会话直接返回启用绑定；
     * GROUP 会话在内容 {@code @机器人} 时返回启用绑定，缺失则自愈创建。
     *
     * @param conversation 会话
     * @param content      用户消息内容
     * @return 绑定；不满足触发条件返回 {@code null}
     */
    private ImAgentBinding resolveBinding(ImConversation conversation, String content) {
        if (TYPE_BOT.equals(conversation.getType())) {
            return enabledBinding(conversation.getId());
        }
        if (!ImConversationService.TYPE_GROUP.equals(conversation.getType()) || !mentionsBot(content)) {
            return null;
        }
        ImAgentBinding binding = enabledBinding(conversation.getId());
        if (binding != null) {
            return binding;
        }
        ImAgentBinding ensured = botService.ensureGroupBinding(conversation.getId());
        return ensured != null && Integer.valueOf(BINDING_ENABLED).equals(ensured.getEnabled()) ? ensured : null;
    }

    /**
     * 判断消息是否包含 {@code @机器人名}。
     *
     * @param content 消息内容
     * @return 包含返回 {@code true}
     */
    private boolean mentionsBot(String content) {
        String botName = botService.botName();
        return StringUtils.hasText(botName) && content.contains("@" + botName);
    }

    /**
     * 原子占位：同一条消息只允许一次触发。
     *
     * <p>Redis 不可用时放行（宁可重复回答，不丢回答），仅记录告警。</p>
     *
     * @param messageId 触发消息ID
     * @return 首次占位返回 {@code true}
     */
    private boolean claimTrigger(Long messageId) {
        if (messageId == null) {
            return false;
        }
        try {
            Boolean claimed = redisTemplate.opsForValue()
                    .setIfAbsent(TRIGGER_KEY_PREFIX + messageId, "1", Duration.ofHours(TRIGGER_TTL_HOURS));
            return Boolean.TRUE.equals(claimed);
        } catch (RuntimeException e) {
            log.warn("机器人触发去重键写入失败，按未触发继续: messageId={}", messageId, e);
            return true;
        }
    }

    /**
     * 执行一次流式回答（独立线程）。
     *
     * @param conversationId 会话ID
     * @param requestMessageId 触发消息ID
     * @param userId           提问用户ID
     * @param question         问题文本
     * @param binding          会话机器人绑定
     * @param permissionCodes  权限码
     */
    private void execute(Long conversationId, Long requestMessageId, Long userId, String question,
                         ImAgentBinding binding, String permissionCodes) {
        ImAgentRun run = startRun(conversationId, requestMessageId);
        RunState state = new RunState();
        long started = System.currentTimeMillis();
        Long botUserId = botService.botUserId();
        try {
            agentStreamClient.stream(requestBody(binding, question), userId, permissionCodes,
                    (event, data) -> handleEvent(run, conversationId, binding, event, data, state));
            if (state.awaitingConfirm) {
                return;
            }
            if (!state.done) {
                throw new IOException("智能体未返回完成事件");
            }
            ImMessage reply = persistReply(conversationId, botUserId, SENDER_BOT, CONTENT_MARKDOWN,
                    state.answer.toString());
            finishRun(run, STATUS_SUCCESS, state, reply.getId());
            broadcaster.broadcast(conversationId, ImMessageService.toVo(reply));
            broadcaster.broadcastFrame(conversationId, WsFrames.agentDone(conversationId, run.getId(), reply.getId(),
                    state.tokens, System.currentTimeMillis() - started, state.degraded));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            degrade(conversationId, run, state, botUserId, INTERRUPTED_HINT, e);
        } catch (Exception e) {
            degrade(conversationId, run, state, botUserId, DEGRADED_HINT, e);
        }
    }

    /**
     * 降级：落系统提示消息、标记运行失败并推送失败帧。
     *
     * @param conversationId 会话ID
     * @param run            运行记录
     * @param state          运行状态
     * @param botUserId      机器人ID（系统提示的发送者）
     * @param hint           提示文案
     * @param cause          原始异常
     */
    private void degrade(Long conversationId, ImAgentRun run, RunState state, Long botUserId,
                         String hint, Exception cause) {
        log.warn("智能体回答失败: conversationId={}, runId={}", conversationId, run.getId(), cause);
        ImMessage reply = persistReply(conversationId, botUserId, SENDER_SYSTEM, CONTENT_TEXT, hint);
        finishRun(run, STATUS_FAILED, state, reply.getId());
        broadcaster.broadcastFrame(conversationId, WsFrames.agentError(conversationId, run.getId(), hint));
        broadcaster.broadcast(conversationId, ImMessageService.toVo(reply));
    }

    /**
     * 处理单个 SSE 事件并转换为 WS 帧。
     *
     * @param run            运行记录
     * @param conversationId 会话ID
     * @param binding        机器人绑定
     * @param event          事件名
     * @param data           事件 JSON
     * @param state          运行状态
     * @throws IOException 事件不合法或智能体返回错误
     */
    private void handleEvent(ImAgentRun run, Long conversationId, ImAgentBinding binding,
                             String event, String data, RunState state) throws IOException {
        JsonNode node;
        try {
            node = objectMapper.readTree(data);
        } catch (RuntimeException e) {
            throw new IOException("智能体事件解析失败", e);
        }
        switch (event) {
            case "meta" -> handleMeta(run, conversationId, binding, node, state);
            case "delta" -> handleDelta(run, conversationId, node, state);
            case "tool" -> state.tools.add(AgentTraceMasker.mask(copyValue(node)));
            case "confirm" -> handleConfirm(run, conversationId, node, state);
            case "done" -> handleDone(node, state);
            case "error" -> throw new IOException(text(node, "message", "智能体执行失败"));
            default -> log.debug("忽略未知智能体事件: event={}", event);
        }
    }

    /**
     * 处理 {@code meta} 事件：回填智能体会话ID并推送元信息帧。
     *
     * @param run            运行记录
     * @param conversationId 会话ID
     * @param binding        机器人绑定
     * @param node           事件 JSON
     * @param state          运行状态
     */
    private void handleMeta(ImAgentRun run, Long conversationId, ImAgentBinding binding,
                            JsonNode node, RunState state) {
        String agentConversationId = text(node, "conversationId", null);
        state.degraded = boolValue(node, "degraded");
        if (StringUtils.hasText(agentConversationId)
                && !agentConversationId.equals(binding.getAgentConversationId())) {
            LambdaUpdateWrapper<ImAgentBinding> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(ImAgentBinding::getId, binding.getId());
            wrapper.set(ImAgentBinding::getAgentConversationId, agentConversationId);
            bindingMapper.update(null, wrapper);
        }
        broadcaster.broadcastFrame(conversationId,
                WsFrames.agentMeta(conversationId, run.getId(), agentConversationId,
                        text(node, "intent", null), state.degraded));
    }

    /**
     * 处理 {@code delta} 事件：累加文本并推送增量帧。
     *
     * @param run            运行记录
     * @param conversationId 会话ID
     * @param node           事件 JSON
     * @param state          运行状态
     */
    private void handleDelta(ImAgentRun run, Long conversationId, JsonNode node, RunState state) {
        String chunk = text(node, "text", "");
        if (!StringUtils.hasText(chunk)) {
            return;
        }
        state.answer.append(chunk);
        broadcaster.broadcastFrame(conversationId, WsFrames.agentDelta(conversationId, run.getId(), chunk));
    }

    /**
     * 处理 {@code confirm} 事件：落确认卡片消息、运行置 {@code WAITING_CONFIRM} 并推送卡片帧。
     *
     * <p>卡片内容为 JSON（{@code type/confirmationId/operation/summary/permission/expiresAt}），
     * 前端按 {@code content_type=CARD} 渲染；未确认前不会触发任何写操作。</p>
     *
     * @param run            运行记录
     * @param conversationId 会话ID
     * @param node           事件 JSON
     * @param state          运行状态
     */
    private void handleConfirm(ImAgentRun run, Long conversationId, JsonNode node, RunState state) {
        String confirmationId = text(node, "confirmationId", null);
        if (!StringUtils.hasText(confirmationId)) {
            log.warn("确认事件缺少 confirmationId，忽略: runId={}", run.getId());
            return;
        }
        String operation = text(node, "operation", "");
        String summary = text(node, "summary", "");
        String permission = text(node, "permission", "");
        String expiresAt = text(node, "expiresAt", "");
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("type", "platform_op");
        // runId 随卡片落库，刷新后仍能凭 content 调用 /im/agent-runs/{id}/confirm
        card.put("runId", String.valueOf(run.getId()));
        card.put("confirmationId", confirmationId);
        card.put("operation", operation);
        card.put("summary", summary);
        card.put("permission", permission);
        card.put("expiresAt", expiresAt);
        String cardJson;
        try {
            cardJson = objectMapper.writeValueAsString(card);
        } catch (RuntimeException e) {
            throw new IllegalStateException("确认卡片序列化失败", e);
        }
        ImMessage message = appendMessage(conversationId, botService.botUserId(), SENDER_BOT, CONTENT_CARD, cardJson);
        LambdaUpdateWrapper<ImAgentRun> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ImAgentRun::getId, run.getId());
        wrapper.set(ImAgentRun::getStatus, STATUS_WAITING_CONFIRM);
        wrapper.set(ImAgentRun::getConfirmationJson, cardJson);
        runMapper.update(null, wrapper);
        broadcaster.broadcast(conversationId, ImMessageService.toVo(message));
        broadcaster.broadcastFrame(conversationId, WsFrames.agentConfirm(conversationId, run.getId(), message.getId(),
                confirmationId, operation, summary, permission, expiresAt));
        state.awaitingConfirm = true;
        state.done = true;
    }

    /**
     * 追加一条机器人/系统消息（供确认结果回写等复用）。
     *
     * @param conversationId 会话ID
     * @param senderId       发送者ID
     * @param senderType     发送者类型（BOT/SYSTEM）
     * @param contentType    内容类型
     * @param content        消息内容
     * @return 已落库消息
     */
    public ImMessage appendMessage(Long conversationId, Long senderId, String senderType,
                                   String contentType, String content) {
        return persistReply(conversationId, senderId, senderType, contentType, content);
    }

    /**
     * 处理 {@code done} 事件：记录 token、降级标记与引用清单。
     *
     * @param node  事件 JSON
     * @param state 运行状态
     */
    private void handleDone(JsonNode node, RunState state) {
        state.done = true;
        state.tokens = node.path("tokens").asInt(0);
        state.degraded = state.degraded || boolValue(node, "degraded");
        JsonNode citations = node.get("citations");
        if (citations != null && citations.isArray()) {
            List<Object> items = new ArrayList<>(citations.size());
            for (JsonNode item : citations) {
                items.add(AgentTraceMasker.mask(copyValue(item)));
            }
            state.citations = items;
        }
    }

    /**
     * 创建运行记录（状态 RUNNING）。
     *
     * @param conversationId   会话ID
     * @param requestMessageId 触发消息ID
     * @return 运行记录
     */
    private ImAgentRun startRun(Long conversationId, Long requestMessageId) {
        ImAgentRun run = new ImAgentRun();
        run.setConversationId(conversationId);
        run.setRequestMessageId(requestMessageId);
        run.setStatus(STATUS_RUNNING);
        run.setStartedAt(LocalDateTime.now());
        runMapper.insert(run);
        return run;
    }

    /**
     * 结束运行记录：写状态、回复消息ID、轨迹与结束时间。
     *
     * @param run            运行记录
     * @param status         最终状态
     * @param state          运行状态
     * @param responseMessageId 回复消息ID
     */
    private void finishRun(ImAgentRun run, String status, RunState state, Long responseMessageId) {
        LambdaUpdateWrapper<ImAgentRun> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ImAgentRun::getId, run.getId());
        wrapper.set(ImAgentRun::getStatus, status);
        wrapper.set(ImAgentRun::getResponseMessageId, responseMessageId);
        wrapper.set(ImAgentRun::getFinishedAt, LocalDateTime.now());
        wrapper.set(ImAgentRun::getToolTraceJson, traceJson(state));
        runMapper.update(null, wrapper);
    }

    /**
     * 序列化工具轨迹与引用。
     *
     * @param state 运行状态
     * @return JSON 文本
     */
    private String traceJson(RunState state) {
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("tools", state.tools);
        trace.put("citations", state.citations);
        try {
            return objectMapper.writeValueAsString(trace);
        } catch (RuntimeException e) {
            log.warn("工具轨迹序列化失败", e);
            return null;
        }
    }

    /**
     * 落库机器人回复并推进会话游标。
     *
     * @param conversationId 会话ID
     * @param senderId       发送者ID（机器人或系统）
     * @param senderType     发送者类型
     * @param contentType    内容类型
     * @param content        回答内容
     * @return 已落库消息
     */
    private ImMessage persistReply(Long conversationId, Long senderId, String senderType,
                                   String contentType, String content) {
        for (int attempt = 0; attempt < MAX_SEQ_ATTEMPTS; attempt++) {
            ImConversation conversation = conversationMapper.selectById(conversationId);
            long lastSeq = conversation == null || conversation.getLastSeq() == null ? 0L : conversation.getLastSeq();
            long seq = seqAllocator.next(conversationId, lastSeq);
            ImMessage message = new ImMessage();
            message.setConversationId(conversationId);
            message.setSeq(seq);
            message.setSenderId(senderId);
            message.setSenderType(senderType);
            message.setContentType(contentType);
            message.setContent(content);
            message.setStatus(1);
            try {
                messageMapper.insert(message);
                touchConversation(conversationId, seq, message.getId());
                return message;
            } catch (DuplicateKeyException e) {
                log.warn("机器人回复序号冲突，重新分配: conversationId={}, seq={}", conversationId, seq);
            }
        }
        throw new IllegalStateException("机器人回复落库失败");
    }

    /**
     * 推进会话最后序号与活跃时间。
     *
     * @param conversationId 会话ID
     * @param seq            消息序号
     * @param messageId      消息ID
     */
    private void touchConversation(Long conversationId, Long seq, Long messageId) {
        LambdaUpdateWrapper<ImConversation> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ImConversation::getId, conversationId);
        wrapper.lt(ImConversation::getLastSeq, seq);
        wrapper.set(ImConversation::getLastSeq, seq);
        wrapper.set(ImConversation::getLastMessageId, messageId);
        wrapper.set(ImConversation::getLastActiveAt, LocalDateTime.now());
        conversationMapper.update(null, wrapper);
    }

    /**
     * 查询会话启用的机器人绑定。
     *
     * @param conversationId 会话ID
     * @return 绑定；不存在返回 {@code null}
     */
    private ImAgentBinding enabledBinding(Long conversationId) {
        LambdaQueryWrapper<ImAgentBinding> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ImAgentBinding::getConversationId, conversationId);
        wrapper.eq(ImAgentBinding::getEnabled, BINDING_ENABLED);
        wrapper.last("LIMIT 1");
        return bindingMapper.selectOne(wrapper);
    }

    /**
     * 构造智能体提问请求体。
     *
     * @param binding  机器人绑定
     * @param question 问题文本
     * @return JSON 文本
     */
    private String requestBody(ImAgentBinding binding, String question) {
        Map<String, Object> body = new LinkedHashMap<>();
        Long agentConversationId = parseId(binding.getAgentConversationId());
        if (agentConversationId != null) {
            body.put("conversationId", agentConversationId);
        }
        body.put("question", question.length() > MAX_QUESTION_LENGTH
                ? question.substring(0, MAX_QUESTION_LENGTH) : question);
        try {
            return objectMapper.writeValueAsString(body);
        } catch (RuntimeException e) {
            throw new IllegalStateException("智能体请求序列化失败", e);
        }
    }

    /**
     * 解析智能体会话ID。
     *
     * @param value 原始文本
     * @return 数值ID；非法或空返回 {@code null}
     */
    private static Long parseId(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 读取文本字段。
     *
     * @param node          JSON 对象
     * @param field         字段名
     * @param defaultValue  缺省值
     * @return 字段文本
     */
    private static String text(JsonNode node, String field, String defaultValue) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? defaultValue : value.asText();
    }

    /**
     * 读取布尔字段。
     *
     * @param node  JSON 对象
     * @param field 字段名
     * @return 字段值；缺失时返回 {@code false}
     */
    private static boolean boolValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && !value.isNull() && value.asBoolean(false);
    }

    /**
     * 把 JSON 节点转为通用对象（便于落库与再序列化）。
     *
     * @param node JSON 节点
     * @return 通用对象
     */
    private Object copyValue(JsonNode node) {
        return objectMapper.convertValue(node, Object.class);
    }

    /**
     * 单次运行的中间状态。
     */
    private static final class RunState {

        /** 已累积的回答文本 */
        private final StringBuilder answer = new StringBuilder();

        /** 工具调用轨迹 */
        private final List<Object> tools = new ArrayList<>();

        /** 引用清单 */
        private List<Object> citations = new ArrayList<>();

        /** 是否降级 */
        private boolean degraded;

        /** token 估算 */
        private int tokens;

        /** 是否已收到 done */
        private boolean done;

        /** 是否已进入待确认状态 */
        private boolean awaitingConfirm;
    }
}
