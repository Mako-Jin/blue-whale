package com.bluewhale.im.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.im.dto.CreateConversationRequest;
import com.bluewhale.im.entity.ImConversation;
import com.bluewhale.im.entity.ImConversationMember;
import com.bluewhale.im.mapper.ImConversationMapper;
import com.bluewhale.im.mapper.ImConversationMemberMapper;
import com.bluewhale.im.vo.ConversationVO;
import com.bluewhale.im.vo.MessageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 会话服务：创建（单聊/群聊）、我的会话列表、已读上报。
 *
 * <p>所有读写均以网关 {@code X-User-Id} 为隔离条件，非成员一律拒绝。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class ImConversationService {

    /** 会话类型：单聊 */
    public static final String TYPE_SINGLE = "SINGLE";

    /** 会话类型：群聊 */
    public static final String TYPE_GROUP = "GROUP";

    /** 成员类型：用户 */
    private static final String MEMBER_USER = "USER";

    /** 会话角色：群主 */
    private static final String ROLE_OWNER = "OWNER";

    /** 会话角色：成员 */
    private static final String ROLE_MEMBER = "MEMBER";

    /** 群成员上限（含创建人） */
    private static final int MAX_MEMBERS = 200;

    /** 会话列表单次上限 */
    private static final int MAX_CONVERSATIONS = 200;

    /** 时间格式 */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final ImConversationMapper conversationMapper;

    private final ImConversationMemberMapper memberMapper;

    private final ImConversationGuard guard;

    private final ImMessageService messageService;

    /**
     * 创建会话。
     *
     * <p>{@code SINGLE} 必须且只能指定 1 位对方；{@code GROUP} 必须填写标题，
     * 成员总数（含创建人）不超过 200。</p>
     *
     * @param userId  当前用户ID
     * @param request 创建请求
     * @return 新会话
     */
    @Transactional
    public ImConversation create(Long userId, CreateConversationRequest request) {
        String type = request.getType();
        List<Long> others = normalizeMembers(request.getMemberIds(), userId);
        if (TYPE_SINGLE.equals(type) && others.size() != 1) {
            throw new BizException("单聊必须且只能指定 1 位对方");
        }
        if (!TYPE_SINGLE.equals(type)) {
            if (!StringUtils.hasText(request.getTitle())) {
                throw new BizException("群聊必须填写标题");
            }
            if (others.size() > MAX_MEMBERS - 1) {
                throw new BizException("群成员上限 200 人");
            }
        }
        ImConversation conversation = new ImConversation();
        conversation.setType(type);
        conversation.setTitle(TYPE_SINGLE.equals(type) ? null : request.getTitle().trim());
        conversation.setOwnerId(userId);
        conversation.setLastSeq(0L);
        conversation.setLastActiveAt(LocalDateTime.now());
        conversationMapper.insert(conversation);
        addMember(conversation.getId(), userId, ROLE_OWNER);
        for (Long other : others) {
            addMember(conversation.getId(), other, ROLE_MEMBER);
        }
        return conversation;
    }

    /**
     * 我的会话列表（按最后活跃时间倒序，含未读数与最后一条消息摘要）。
     *
     * @param userId 当前用户ID
     * @return 会话列表
     */
    public List<ConversationVO> list(Long userId) {
        LambdaQueryWrapper<ImConversationMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ImConversationMember::getUserId, userId);
        wrapper.orderByDesc(ImConversationMember::getId);
        wrapper.last("LIMIT " + MAX_CONVERSATIONS);
        List<ImConversationMember> members = memberMapper.selectList(wrapper);
        if (members.isEmpty()) {
            return List.of();
        }
        List<Long> conversationIds = new ArrayList<>(members.size());
        for (ImConversationMember member : members) {
            conversationIds.add(member.getConversationId());
        }
        Map<Long, ImConversation> conversationById = new HashMap<>(conversationIds.size());
        for (ImConversation conversation : conversationMapper.selectBatchIds(conversationIds)) {
            conversationById.put(conversation.getId(), conversation);
        }
        Set<Long> lastMessageIds = new LinkedHashSet<>();
        for (ImConversation conversation : conversationById.values()) {
            if (conversation.getLastMessageId() != null) {
                lastMessageIds.add(conversation.getLastMessageId());
            }
        }
        Map<Long, MessageVO> summaries = messageService.summaries(lastMessageIds);
        List<ConversationVO> result = new ArrayList<>(members.size());
        for (ImConversationMember member : members) {
            ImConversation conversation = conversationById.get(member.getConversationId());
            if (conversation == null) {
                continue;
            }
            long unread = Math.max(0L, nvl(conversation.getLastSeq()) - nvl(member.getLastReadSeq()));
            result.add(toVo(conversation, unread, summaries.get(conversation.getLastMessageId())));
        }
        result.sort(Comparator.comparing(ConversationVO::getLastActiveAt,
                Comparator.nullsLast(Comparator.<String>reverseOrder())));
        return result;
    }

    /**
     * 已读上报：游标收敛到 {@code [0, lastSeq]} 且只前进不后退。
     *
     * @param conversationId 会话ID
     * @param userId         当前用户ID
     * @param seq            客户端已读到的最大序号
     */
    public void markRead(Long conversationId, Long userId, long seq) {
        ImConversation conversation = guard.requireMember(conversationId, userId);
        long target = Math.min(Math.max(seq, 0L), nvl(conversation.getLastSeq()));
        if (target <= 0L) {
            return;
        }
        LambdaUpdateWrapper<ImConversationMember> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ImConversationMember::getConversationId, conversationId);
        wrapper.eq(ImConversationMember::getUserId, userId);
        wrapper.lt(ImConversationMember::getLastReadSeq, target);
        wrapper.set(ImConversationMember::getLastReadSeq, target);
        memberMapper.update(null, wrapper);
    }

    /**
     * 会话实体转视图对象（创建返回与列表复用）。
     *
     * @param conversation 会话实体
     * @param unread       未读数
     * @param lastMessage  最后一条消息摘要
     * @return 视图对象
     */
    public static ConversationVO toVo(ImConversation conversation, long unread, MessageVO lastMessage) {
        return ConversationVO.builder()
                .id(String.valueOf(conversation.getId()))
                .type(conversation.getType())
                .title(conversation.getTitle())
                .ownerId(conversation.getOwnerId() == null ? null : String.valueOf(conversation.getOwnerId()))
                .lastSeq(conversation.getLastSeq())
                .unread(unread)
                .lastActiveAt(format(conversation.getLastActiveAt()))
                .lastMessage(lastMessage)
                .build();
    }

    /**
     * 归一化成员ID：去空、去重、排除创建人。
     *
     * @param memberIds 原始成员ID
     * @param ownerId   创建人ID
     * @return 去重后的成员ID
     */
    private static List<Long> normalizeMembers(List<Long> memberIds, Long ownerId) {
        Set<Long> unique = new LinkedHashSet<>();
        for (Long memberId : memberIds) {
            if (memberId != null && !Objects.equals(memberId, ownerId)) {
                unique.add(memberId);
            }
        }
        return new ArrayList<>(unique);
    }

    /**
     * 写入会话成员。
     *
     * @param conversationId 会话ID
     * @param userId         成员用户ID
     * @param role           会话角色
     */
    private void addMember(Long conversationId, Long userId, String role) {
        ImConversationMember member = new ImConversationMember();
        member.setConversationId(conversationId);
        member.setUserId(userId);
        member.setMemberType(MEMBER_USER);
        member.setRole(role);
        member.setLastReadSeq(0L);
        member.setJoinTime(LocalDateTime.now());
        memberMapper.insert(member);
    }

    /**
     * 空值转 0。
     *
     * @param value 原值
     * @return 非空值
     */
    private static long nvl(Long value) {
        return value == null ? 0L : value;
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
