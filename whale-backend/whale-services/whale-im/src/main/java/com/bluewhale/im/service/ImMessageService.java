package com.bluewhale.im.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.im.dto.SendMessageRequest;
import com.bluewhale.im.entity.ImConversation;
import com.bluewhale.im.entity.ImConversationMember;
import com.bluewhale.im.entity.ImMessage;
import com.bluewhale.im.mapper.ImConversationMapper;
import com.bluewhale.im.mapper.ImConversationMemberMapper;
import com.bluewhale.im.mapper.ImMessageMapper;
import com.bluewhale.im.support.AttachmentPayloadValidator;
import com.bluewhale.im.vo.MessagePageVO;
import com.bluewhale.im.vo.MessageVO;
import com.bluewhale.im.ws.ImMessageBroadcaster;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 消息服务：发送（幂等 + 序号分配）、历史游标拉取、会话最后一条摘要。
 *
 * <p>可靠性口径见规划 §5.3：{@code clientMsgId} 幂等、序号单调、断线按 {@code afterSeq} 补拉。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
public class ImMessageService {

    /** 发送者类型：用户 */
    public static final String SENDER_USER = "USER";

    /** 发送者类型：机器人（站内通知） */
    private static final String SENDER_BOT = "BOT";

    /** 内容类型：纯文本（站内通知） */
    private static final String CONTENT_TYPE_TEXT = "TEXT";

    /** 会话类型：群聊 */
    private static final String TYPE_GROUP = "GROUP";

    /** 会话角色：群主 */
    private static final String ROLE_OWNER = "OWNER";

    /** 会话角色：管理员 */
    private static final String ROLE_ADMIN = "ADMIN";

    /** 消息状态：正常 */
    private static final int STATUS_NORMAL = 1;

    /** 消息状态：已撤回 */
    private static final int STATUS_RECALLED = 0;

    /** 默认分页条数 */
    private static final int DEFAULT_LIMIT = 30;

    /** 单次拉取上限 */
    private static final int MAX_LIMIT = 100;

    /** 序号冲突重试次数 */
    private static final int MAX_SEQ_ATTEMPTS = 3;

    /** 时间格式 */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final ImMessageMapper messageMapper;

    private final ImConversationMapper conversationMapper;

    private final ImConversationMemberMapper memberMapper;

    private final ImConversationGuard guard;

    private final MessageSeqAllocator seqAllocator;

    private final ImMessageBroadcaster broadcaster;

    private final AttachmentPayloadValidator attachmentValidator;

    /** 允许撤回的时间窗口（秒），自消息落库起算 */
    private final long recallWindowSeconds;

    /**
     * 构造消息服务。
     *
     * @param messageMapper       消息 Mapper
     * @param conversationMapper  会话 Mapper
     * @param memberMapper        成员 Mapper
     * @param guard               成员校验
     * @param seqAllocator        序号分配器
     * @param broadcaster         WS 广播器
     * @param attachmentValidator 附件内容校验
     * @param recallWindowSeconds 撤回时限（秒）
     */
    public ImMessageService(ImMessageMapper messageMapper, ImConversationMapper conversationMapper,
                            ImConversationMemberMapper memberMapper, ImConversationGuard guard,
                            MessageSeqAllocator seqAllocator, ImMessageBroadcaster broadcaster,
                            AttachmentPayloadValidator attachmentValidator,
                            @Value("${whale.im.recall-window-seconds:120}") long recallWindowSeconds) {
        this.messageMapper = messageMapper;
        this.conversationMapper = conversationMapper;
        this.memberMapper = memberMapper;
        this.guard = guard;
        this.seqAllocator = seqAllocator;
        this.broadcaster = broadcaster;
        this.attachmentValidator = attachmentValidator;
        this.recallWindowSeconds = recallWindowSeconds;
    }

    /**
     * 发送消息（同一会话内 {@code clientMsgId} 幂等）。
     *
     * @param conversationId 会话ID
     * @param userId         当前用户ID
     * @param request        发送请求
     * @return 已落库消息；重复提交时返回首次落库的消息
     */
    @Transactional
    public ImMessage send(Long conversationId, Long userId, SendMessageRequest request) {
        ImConversation conversation = guard.requireMember(conversationId, userId);
        ImMessage existing = findByClientMsgId(conversationId, request.getClientMsgId());
        if (existing != null) {
            return existing;
        }
        ImMessage message = insertWithRetry(conversation, userId, request);
        touchConversation(conversationId, message.getSeq(), message.getId());
        markSenderRead(conversationId, userId, message.getSeq());
        pushAfterCommit(message);
        return message;
    }

    /**
     * 以机器人身份发送纯文本消息（服务间内网站内通知）。
     *
     * <p>{@code clientMsgId} 在同一会话内幂等；发送方记为机器人，接收人未读数自然递增。</p>
     *
     * @param conversationId 会话ID
     * @param botUserId      机器人用户ID
     * @param content        通知正文
     * @param clientMsgId    幂等键
     * @return 已落库消息；重复提交时返回首次落库的消息
     */
    @Transactional
    public ImMessage sendAsBot(Long conversationId, Long botUserId, String content, String clientMsgId) {
        ImConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "会话不存在");
        }
        ImMessage existing = findByClientMsgId(conversationId, clientMsgId);
        if (existing != null) {
            return existing;
        }
        ImMessage message = insertBotWithRetry(conversation, botUserId, content, clientMsgId);
        touchConversation(conversationId, message.getSeq(), message.getId());
        pushAfterCommit(message);
        return message;
    }

    /**
     * 历史消息游标拉取。
     *
     * <p>{@code afterSeq} 优先（断线补拉，按 seq 升序）；否则按 {@code beforeSeq} 向前翻页（倒序取数后反转为升序）；
     * 两者都为空时取最近一页。</p>
     *
     * @param conversationId 会话ID
     * @param userId         当前用户ID
     * @param beforeSeq      取该序号之前（可空）
     * @param afterSeq       取该序号之后（可空）
     * @param limit          条数上限
     * @return 升序消息页
     */
    public MessagePageVO history(Long conversationId, Long userId, Long beforeSeq, Long afterSeq, int limit) {
        guard.requireMember(conversationId, userId);
        int size = normalizeLimit(limit);
        boolean ascending = afterSeq != null;
        LambdaQueryWrapper<ImMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ImMessage::getConversationId, conversationId);
        if (ascending) {
            wrapper.gt(ImMessage::getSeq, afterSeq).orderByAsc(ImMessage::getSeq);
        } else {
            if (beforeSeq != null) {
                wrapper.lt(ImMessage::getSeq, beforeSeq);
            }
            wrapper.orderByDesc(ImMessage::getSeq);
        }
        wrapper.last("LIMIT " + (size + 1));
        List<ImMessage> messages = new ArrayList<>(messageMapper.selectList(wrapper));
        boolean hasMore = messages.size() > size;
        if (hasMore) {
            messages = new ArrayList<>(messages.subList(0, size));
        }
        if (!ascending) {
            Collections.reverse(messages);
        }
        List<MessageVO> records = new ArrayList<>(messages.size());
        for (ImMessage message : messages) {
            records.add(toVo(message));
        }
        return MessagePageVO.builder().records(records).hasMore(hasMore).build();
    }

    /**
     * 撤回消息：发送者本人（限时）或群主/管理员（不限时）可撤回。
     *
     * <p>撤回后清空消息内容并将状态置为 {@value #STATUS_RECALLED}，向会话成员推送
     * {@code message.recalled} 帧；重复撤回幂等返回，不重复广播。</p>
     *
     * @param conversationId 会话ID
     * @param userId         当前用户ID
     * @param messageId      消息ID
     */
    @Transactional
    public void recall(Long conversationId, Long userId, Long messageId) {
        ImConversation conversation = guard.requireMember(conversationId, userId);
        ImMessage message = messageMapper.selectById(messageId);
        if (message == null || !Objects.equals(message.getConversationId(), conversationId)) {
            throw new BizException(ErrorCode.NOT_FOUND, "消息不存在或无权撤回");
        }
        ImConversationMember operator = guard.requireMemberRecord(conversationId, userId);
        boolean self = Objects.equals(message.getSenderId(), userId);
        boolean manager = TYPE_GROUP.equals(conversation.getType())
                && operator != null
                && (ROLE_OWNER.equals(operator.getRole()) || ROLE_ADMIN.equals(operator.getRole()));
        if (!self && !manager) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能撤回自己发送的消息");
        }
        if (Integer.valueOf(STATUS_RECALLED).equals(message.getStatus())) {
            return;
        }
        if (self && isRecallExpired(message)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "超过撤回时限");
        }
        LambdaUpdateWrapper<ImMessage> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ImMessage::getId, messageId);
        wrapper.eq(ImMessage::getStatus, STATUS_NORMAL);
        wrapper.set(ImMessage::getStatus, STATUS_RECALLED);
        wrapper.set(ImMessage::getContent, null);
        messageMapper.update(null, wrapper);
        pushRecalledAfterCommit(conversationId, messageId, message.getSeq(), userId);
    }

    /**
     * 批量读取消息摘要（会话列表展示最后一条消息）。
     *
     * @param messageIds 消息ID集合
     * @return 消息ID → 视图对象
     */
    public Map<Long, MessageVO> summaries(Collection<Long> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return Map.of();
        }
        List<ImMessage> messages = messageMapper.selectBatchIds(messageIds);
        Map<Long, MessageVO> result = new HashMap<>(messages.size());
        for (ImMessage message : messages) {
            result.put(message.getId(), toVo(message));
        }
        return result;
    }

    /**
     * 消息实体转视图对象。
     *
     * @param message 消息实体
     * @return 视图对象
     */
    public static MessageVO toVo(ImMessage message) {
        return MessageVO.builder()
                .id(String.valueOf(message.getId()))
                .conversationId(String.valueOf(message.getConversationId()))
                .seq(message.getSeq())
                .senderId(message.getSenderId() == null ? null : String.valueOf(message.getSenderId()))
                .senderType(message.getSenderType())
                .contentType(message.getContentType())
                .content(message.getContent())
                .clientMsgId(message.getClientMsgId())
                .replyTo(message.getReplyTo() == null ? null : String.valueOf(message.getReplyTo()))
                .status(message.getStatus())
                .createTime(format(message.getCreateTime()))
                .build();
    }

    /**
     * 插入消息；序号冲突时重新分配（最多 {@value #MAX_SEQ_ATTEMPTS} 次）。
     *
     * @param conversation 会话
     * @param userId       发送者ID
     * @param request      发送请求
     * @return 落库消息
     */
    private ImMessage insertWithRetry(ImConversation conversation, Long userId, SendMessageRequest request) {
        for (int attempt = 0; attempt < MAX_SEQ_ATTEMPTS; attempt++) {
            long seq = seqAllocator.next(conversation.getId(), conversation.getLastSeq());
            ImMessage message = buildMessage(conversation.getId(), userId, request, seq);
            try {
                messageMapper.insert(message);
                return message;
            } catch (DuplicateKeyException e) {
                ImMessage existing = findByClientMsgId(conversation.getId(), request.getClientMsgId());
                if (existing != null) {
                    return existing;
                }
                log.warn("消息序号冲突，重新分配: conversationId={}, seq={}", conversation.getId(), seq);
            }
        }
        throw new BizException(ErrorCode.LOCK_ACQUIRE_FAILED, "消息发送繁忙，请稍后重试");
    }

    /**
     * 组装待落库消息。
     *
     * @param conversationId 会话ID
     * @param userId         发送者ID
     * @param request        发送请求
     * @param seq            已分配序号
     * @return 消息实体
     */
    private ImMessage buildMessage(Long conversationId, Long userId, SendMessageRequest request, long seq) {
        return buildMessage(conversationId, userId, SENDER_USER, request.getContentType(),
                attachmentValidator.validateAndNormalize(request.getContentType(), request.getContent()),
                request.getClientMsgId(), request.getReplyTo(), seq);
    }

    /**
     * 插入机器人消息；序号冲突时重新分配（最多 {@value #MAX_SEQ_ATTEMPTS} 次）。
     *
     * @param conversation 会话
     * @param botUserId    机器人用户ID
     * @param content      通知正文
     * @param clientMsgId  幂等键
     * @return 落库消息
     */
    private ImMessage insertBotWithRetry(ImConversation conversation, Long botUserId, String content,
                                         String clientMsgId) {
        long baseSeq = conversation.getLastSeq() == null ? 0L : conversation.getLastSeq();
        for (int attempt = 0; attempt < MAX_SEQ_ATTEMPTS; attempt++) {
            long seq = seqAllocator.next(conversation.getId(), baseSeq);
            ImMessage message = buildMessage(conversation.getId(), botUserId, SENDER_BOT, CONTENT_TYPE_TEXT,
                    content, clientMsgId, null, seq);
            try {
                messageMapper.insert(message);
                return message;
            } catch (DuplicateKeyException e) {
                ImMessage existing = findByClientMsgId(conversation.getId(), clientMsgId);
                if (existing != null) {
                    return existing;
                }
                log.warn("机器人消息序号冲突，重新分配: conversationId={}, seq={}", conversation.getId(), seq);
            }
        }
        throw new BizException(ErrorCode.LOCK_ACQUIRE_FAILED, "消息发送繁忙，请稍后重试");
    }

    /**
     * 组装待落库消息（发送者类型与内容类型由调用方指定）。
     *
     * @param conversationId 会话ID
     * @param senderId       发送者ID
     * @param senderType     发送者类型
     * @param contentType    内容类型
     * @param content        已校验内容
     * @param clientMsgId    幂等键
     * @param replyTo        被回复消息ID
     * @param seq            已分配序号
     * @return 消息实体
     */
    private ImMessage buildMessage(Long conversationId, Long senderId, String senderType, String contentType,
                                   String content, String clientMsgId, Long replyTo, long seq) {
        ImMessage message = new ImMessage();
        message.setConversationId(conversationId);
        message.setSeq(seq);
        message.setSenderId(senderId);
        message.setSenderType(senderType);
        message.setContentType(contentType);
        message.setContent(content);
        message.setClientMsgId(clientMsgId);
        message.setReplyTo(replyTo);
        message.setStatus(STATUS_NORMAL);
        return message;
    }

    /**
     * 按 {@code clientMsgId} 查询已落库消息。
     *
     * @param conversationId 会话ID
     * @param clientMsgId    客户端消息ID
     * @return 已存在消息；不存在返回 {@code null}
     */
    private ImMessage findByClientMsgId(Long conversationId, String clientMsgId) {
        LambdaQueryWrapper<ImMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ImMessage::getConversationId, conversationId);
        wrapper.eq(ImMessage::getClientMsgId, clientMsgId);
        return messageMapper.selectOne(wrapper);
    }

    /**
     * 推进会话游标（仅在更大序号时更新，避免并发回退）。
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
     * 发送者自动已读：游标只前进不后退。
     *
     * @param conversationId 会话ID
     * @param userId         发送者ID
     * @param seq            消息序号
     */
    private void markSenderRead(Long conversationId, Long userId, Long seq) {
        LambdaUpdateWrapper<ImConversationMember> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ImConversationMember::getConversationId, conversationId);
        wrapper.eq(ImConversationMember::getUserId, userId);
        wrapper.lt(ImConversationMember::getLastReadSeq, seq);
        wrapper.set(ImConversationMember::getLastReadSeq, seq);
        memberMapper.update(null, wrapper);
    }

    /**
     * 事务提交后向会话成员推送新消息。
     *
     * <p>提交前推送会让客户端先于落库可见，补拉时查不到该序号；无事务上下文（单测/内部调用）时直接推送。</p>
     *
     * @param message 已落库消息
     */
    private void pushAfterCommit(ImMessage message) {
        MessageVO vo = toVo(message);
        Long conversationId = message.getConversationId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    broadcaster.broadcast(conversationId, vo);
                }
            });
        } else {
            broadcaster.broadcast(conversationId, vo);
        }
    }

    /**
     * 判断消息是否已超过撤回时限（落库时间缺失时按未超时处理）。
     *
     * @param message 消息实体
     * @return 是否已超时
     */
    private boolean isRecallExpired(ImMessage message) {
        LocalDateTime createTime = message.getCreateTime();
        if (createTime == null) {
            return false;
        }
        LocalDateTime deadline = createTime.plus(Duration.ofSeconds(recallWindowSeconds));
        return LocalDateTime.now().isAfter(deadline);
    }

    /**
     * 事务提交后推送撤回通知。
     *
     * @param conversationId 会话ID
     * @param messageId      消息ID
     * @param seq            消息序号
     * @param operatorId     操作人ID
     */
    private void pushRecalledAfterCommit(Long conversationId, Long messageId, Long seq, Long operatorId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    broadcaster.broadcastRecalled(conversationId, messageId, seq, operatorId);
                }
            });
        } else {
            broadcaster.broadcastRecalled(conversationId, messageId, seq, operatorId);
        }
    }

    /**
     * 收敛分页条数到 {@code [1, MAX_LIMIT]}。
     *
     * @param limit 原始条数
     * @return 合法条数
     */
    private static int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    /**
     * 格式化时间。
     *
     * @param time 时间
     * @return ISO-8601 字符串；空值返回 {@code null}
     */
    private static String format(LocalDateTime time) {
        return time == null ? null : time.format(TIME_FORMAT);
    }
}
