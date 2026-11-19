package com.bluewhale.im.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.im.dto.CreateConversationRequest;
import com.bluewhale.im.entity.ImConversation;
import com.bluewhale.im.entity.ImConversationMember;
import com.bluewhale.im.entity.ImMessage;
import com.bluewhale.im.mapper.ImConversationMapper;
import com.bluewhale.im.mapper.ImConversationMemberMapper;
import com.bluewhale.im.mapper.ImMessageMapper;
import com.bluewhale.im.support.ImMessageReceiptClient;
import com.bluewhale.im.support.UserDirectoryClient;
import com.bluewhale.im.vo.ConversationVO;
import com.bluewhale.im.vo.MessageVO;
import com.bluewhale.im.ws.ImMessageBroadcaster;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
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

    private final ImMessageBroadcaster broadcaster;

    private final UserDirectoryClient userDirectoryClient;

    private final ImMessageMapper messageMapper;

    private final ImMessageReceiptClient receiptClient;

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
            throw new BizException(ErrorCode.PARAM_ERROR, "单聊必须且只能指定 1 位对方");
        }
        if (!TYPE_SINGLE.equals(type)) {
            if (!StringUtils.hasText(request.getTitle())) {
                throw new BizException(ErrorCode.PARAM_ERROR, "群聊必须填写标题");
            }
            if (others.size() > MAX_MEMBERS - 1) {
                throw new BizException(ErrorCode.PARAM_ERROR, "群成员上限 200 人");
            }
        }
        requireExistingUsers(others);
        String pairKey = TYPE_SINGLE.equals(type) ? singlePairKey(userId, others.get(0)) : null;
        if (pairKey != null) {
            ImConversation existing = findByPairKey(pairKey);
            if (existing != null) {
                return existing;
            }
        }
        ImConversation conversation = new ImConversation();
        conversation.setType(type);
        conversation.setTitle(TYPE_SINGLE.equals(type) ? null : request.getTitle().trim());
        conversation.setOwnerId(userId);
        conversation.setPairKey(pairKey);
        conversation.setLastSeq(0L);
        conversation.setLastActiveAt(LocalDateTime.now());
        try {
            conversationMapper.insert(conversation);
        } catch (DuplicateKeyException e) {
            // 并发重复创建：唯一索引兜底，返回先落库的会话
            ImConversation existing = pairKey == null ? null : findByPairKey(pairKey);
            if (existing == null) {
                throw e;
            }
            return existing;
        }
        addMember(conversation.getId(), userId, ROLE_OWNER);
        for (Long other : others) {
            addMember(conversation.getId(), other, ROLE_MEMBER);
        }
        return conversation;
    }

    /**
     * 转让群主（仅现任群主可操作，目标必须是群成员）。
     *
     * @param conversationId 会话ID
     * @param operatorId     操作人ID
     * @param targetUserId   新群主ID
     */
    @Transactional
    public void transfer(Long conversationId, Long operatorId, Long targetUserId) {
        ImConversation conversation = guard.requireMember(conversationId, operatorId);
        requireGroup(conversation);
        ImConversationMember operator = guard.requireMemberRecord(conversationId, operatorId);
        if (operator == null || !ROLE_OWNER.equals(operator.getRole())) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅群主可以转让群聊");
        }
        if (Objects.equals(operatorId, targetUserId)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "不能转让给自己");
        }
        ImConversationMember target = guard.requireMemberRecord(conversationId, targetUserId);
        if (target == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "该用户不是群成员");
        }
        LambdaUpdateWrapper<ImConversation> ownerWrapper = new LambdaUpdateWrapper<>();
        ownerWrapper.eq(ImConversation::getId, conversationId);
        ownerWrapper.set(ImConversation::getOwnerId, targetUserId);
        conversationMapper.update(null, ownerWrapper);
        updateRole(conversationId, operatorId, ROLE_MEMBER);
        updateRole(conversationId, targetUserId, ROLE_OWNER);
        broadcaster.broadcastMemberChanged(conversationId, "TRANSFER", targetUserId);
    }

    /**
     * 解散群聊（仅群主可操作，逻辑删除会话后成员不可再访问）。
     *
     * @param conversationId 会话ID
     * @param operatorId     操作人ID
     */
    @Transactional
    public void dissolve(Long conversationId, Long operatorId) {
        ImConversation conversation = guard.requireMember(conversationId, operatorId);
        requireGroup(conversation);
        ImConversationMember operator = guard.requireMemberRecord(conversationId, operatorId);
        if (operator == null || !ROLE_OWNER.equals(operator.getRole())) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅群主可以解散群聊");
        }
        broadcaster.broadcastMemberChanged(conversationId, "DISSOLVED", operatorId);
        conversationMapper.deleteById(conversationId);
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
        long lastSeq = nvl(conversation.getLastSeq());
        long target = Math.min(Math.max(seq, 0L), lastSeq);
        if (target <= 0L) {
            return;
        }
        ImConversationMember member = guard.requireMemberRecord(conversationId, userId);
        long previousRead = member == null ? 0L : nvl(member.getLastReadSeq());
        LambdaUpdateWrapper<ImConversationMember> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ImConversationMember::getConversationId, conversationId);
        wrapper.eq(ImConversationMember::getUserId, userId);
        wrapper.lt(ImConversationMember::getLastReadSeq, target);
        wrapper.set(ImConversationMember::getLastReadSeq, target);
        memberMapper.update(null, wrapper);
        // 游标前进时把本次实际读到的通知回执上报给消息服务（游标不前进则不重复上报）
        if (target > previousRead) {
            reportReadReceipts(conversationId, previousRead, target);
        }
        broadcaster.broadcastUnread(userId, conversationId, remainingUnread(conversationId, userId, lastSeq));
    }

    /**
     * 上报已读回执：取本次游标区间内由消息服务投递的通知，按租户批量回传。
     *
     * @param conversationId 会话ID
     * @param fromExclusive  上次已读序号（不含）
     * @param toInclusive    本次已读序号（含）
     */
    private void reportReadReceipts(Long conversationId, long fromExclusive, long toInclusive) {
        // im_message 实体不含 tenant_id（由租户插件统一维护），这里显式投影列
        QueryWrapper<ImMessage> wrapper = new QueryWrapper<>();
        wrapper.select("tenant_id", "client_msg_id")
                .eq("conversation_id", conversationId)
                .gt("seq", fromExclusive)
                .le("seq", toInclusive)
                .likeRight("client_msg_id", "msg-");
        List<Map<String, Object>> rows = messageMapper.selectMaps(wrapper);
        if (rows.isEmpty()) {
            return;
        }
        Map<Long, List<String>> idsByTenant = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object tenant = row.get("tenant_id");
            Object clientMsgId = row.get("client_msg_id");
            if (tenant == null || clientMsgId == null) {
                continue;
            }
            idsByTenant.computeIfAbsent(((Number) tenant).longValue(), key -> new ArrayList<>())
                    .add(String.valueOf(clientMsgId));
        }
        idsByTenant.forEach(receiptClient::reportRead);
    }

    /**
     * 已读落库后计算该会话的真实未读数（以成员表游标为准，避免客户端上报滞后造成误报）。
     *
     * @param conversationId 会话ID
     * @param userId         用户ID
     * @param lastSeq        会话最新序号
     * @return 未读数（不小于 0）
     */
    private long remainingUnread(Long conversationId, Long userId, long lastSeq) {
        ImConversationMember member = guard.requireMemberRecord(conversationId, userId);
        long readSeq = member == null ? 0L : nvl(member.getLastReadSeq());
        return Math.max(0L, lastSeq - readSeq);
    }

    /**
     * 创建会话并直接装配视图对象（避免 controller 依赖实体，T-18）。
     *
     * @param userId  当前用户ID
     * @param request 创建请求
     * @return 会话视图
     */
    public ConversationVO createVo(Long userId, CreateConversationRequest request) {
        return toVo(create(userId, request), 0L, null);
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
     * 按幂等键查询会话。
     *
     * @param pairKey 幂等键
     * @return 会话；不存在返回 {@code null}
     */
    private ImConversation findByPairKey(String pairKey) {
        LambdaQueryWrapper<ImConversation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ImConversation::getPairKey, pairKey);
        wrapper.last("LIMIT 1");
        return conversationMapper.selectOne(wrapper);
    }

    /**
     * 单聊幂等键（用户ID升序，保证双方口径一致）。
     *
     * @param left  用户A
     * @param right 用户B
     * @return 幂等键
     */
    private static String singlePairKey(Long left, Long right) {
        long min = Math.min(left, right);
        long max = Math.max(left, right);
        return "S:" + min + ":" + max;
    }

    /**
     * 校验会话为群聊。
     *
     * @param conversation 会话
     */
    private static void requireGroup(ImConversation conversation) {
        if (!TYPE_GROUP.equals(conversation.getType())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "仅群聊支持该操作");
        }
    }

    /**
     * 校验成员用户真实存在且启用。
     *
     * <p>用户目录不可用（校验关闭 / 无实例 / 调用失败）时跳过校验，避免 IM 主流程被
     * 认证服务的瞬时故障阻断；仅在拿到结果时拒绝不存在或已停用的用户。</p>
     *
     * @param userIds 待校验用户ID
     */
    private void requireExistingUsers(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return;
        }
        List<Long> missing = userDirectoryClient.missingUserIds(userIds);
        if (!missing.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "以下用户不存在或已停用: " + missing);
        }
    }

    /**
     * 更新成员角色。
     *
     * @param conversationId 会话ID
     * @param userId         成员用户ID
     * @param role           新角色
     */
    private void updateRole(Long conversationId, Long userId, String role) {
        LambdaUpdateWrapper<ImConversationMember> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ImConversationMember::getConversationId, conversationId);
        wrapper.eq(ImConversationMember::getUserId, userId);
        wrapper.set(ImConversationMember::getRole, role);
        memberMapper.update(null, wrapper);
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
