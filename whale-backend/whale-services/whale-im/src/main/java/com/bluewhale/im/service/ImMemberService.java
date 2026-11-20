package com.bluewhale.im.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.im.entity.ImConversation;
import com.bluewhale.im.entity.ImConversationMember;
import com.bluewhale.im.mapper.ImConversationMemberMapper;
import com.bluewhale.im.support.UserDirectoryClient;
import com.bluewhale.im.vo.MemberVO;
import com.bluewhale.im.ws.ImMessageBroadcaster;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 群成员管理：成员列表、添加、移除、主动退群。
 *
 * <p>权限口径：列表对全部成员开放；添加/移除仅群主（OWNER）与管理员（ADMIN）可执行，
 * 且管理员不能移除管理员、任何人都不能移除群主；退群仅限群成员本人（群主除外）。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class ImMemberService {

    /** 会话类型：群聊 */
    private static final String TYPE_GROUP = "GROUP";

    /** 成员类型：用户 */
    private static final String MEMBER_USER = "USER";

    /** 会话角色：群主 */
    private static final String ROLE_OWNER = "OWNER";

    /** 会话角色：管理员 */
    private static final String ROLE_ADMIN = "ADMIN";

    /** 会话角色：普通成员 */
    private static final String ROLE_MEMBER = "MEMBER";

    /** 群成员上限（含群主） */
    private static final int MAX_MEMBERS = 200;

    /** 时间格式 */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final ImConversationMemberMapper memberMapper;

    private final ImConversationGuard guard;

    private final ImMessageBroadcaster broadcaster;

    private final UserDirectoryClient userDirectoryClient;

    /**
     * 查询群成员列表（成员即可查看）。
     *
     * @param conversationId 会话ID
     * @param userId         当前用户ID
     * @return 成员列表（群主在前，其余按加入顺序）
     */
    public List<MemberVO> list(Long conversationId, Long userId) {
        guard.requireMember(conversationId, userId);
        LambdaQueryWrapper<ImConversationMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ImConversationMember::getConversationId, conversationId);
        wrapper.orderByAsc(ImConversationMember::getId);
        List<ImConversationMember> members = new ArrayList<>(memberMapper.selectList(wrapper));
        members.sort((left, right) -> Integer.compare(roleRank(left.getRole()), roleRank(right.getRole())));
        List<MemberVO> result = new ArrayList<>(members.size());
        for (ImConversationMember member : members) {
            result.add(toVo(member));
        }
        return result;
    }

    /**
     * 添加群成员（群主 / 管理员）。
     *
     * <p>已存在的成员静默跳过；超出群成员上限时整体拒绝，不做部分添加。</p>
     *
     * @param conversationId 会话ID
     * @param operatorId     操作人ID
     * @param memberIds      待添加用户ID
     * @return 实际新增的成员
     */
    @Transactional
    public List<MemberVO> add(Long conversationId, Long operatorId, List<Long> memberIds) {
        ImConversation conversation = guard.requireMember(conversationId, operatorId);
        requireGroup(conversation);
        requireManager(conversationId, operatorId);
        Set<Long> candidates = normalizeMembers(memberIds, operatorId);
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<Long> existing = existingUserIds(conversationId);
        List<Long> toInsert = new ArrayList<>(candidates.size());
        for (Long candidate : candidates) {
            if (!existing.contains(candidate)) {
                toInsert.add(candidate);
            }
        }
        if (existing.size() + toInsert.size() > MAX_MEMBERS) {
            throw new BizException(ErrorCode.PARAM_ERROR, "im.member.limit-exceeded", "群成员上限 200 人");
        }
        List<Long> missing = userDirectoryClient.missingUserIds(toInsert);
        if (!missing.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "im.member.users-missing",
                    "以下用户不存在或已停用: " + missing, missing);
        }
        List<MemberVO> added = new ArrayList<>(toInsert.size());
        for (Long candidate : toInsert) {
            ImConversationMember member = newMember(conversationId, candidate);
            try {
                memberMapper.insert(member);
                added.add(toVo(member));
            } catch (DuplicateKeyException e) {
                // 并发重复添加：已由其他请求插入，跳过即可
            }
        }
        for (MemberVO member : added) {
            broadcaster.broadcastMemberChanged(conversationId, "ADD", Long.valueOf(member.getUserId()));
        }
        return added;
    }

    /**
     * 移除群成员（群主 / 管理员）。
     *
     * @param conversationId 会话ID
     * @param operatorId     操作人ID
     * @param targetUserId   被移除用户ID
     */
    @Transactional
    public void remove(Long conversationId, Long operatorId, Long targetUserId) {
        ImConversation conversation = guard.requireMember(conversationId, operatorId);
        requireGroup(conversation);
        ImConversationMember operator = requireManager(conversationId, operatorId);
        if (Objects.equals(operatorId, targetUserId)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "im.member.leave-self", "请使用退群操作移除自己");
        }
        ImConversationMember target = guard.requireMemberRecord(conversationId, targetUserId);
        if (target == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "im.member.not-member", "该用户不是群成员");
        }
        if (ROLE_OWNER.equals(target.getRole())) {
            throw new BizException(ErrorCode.FORBIDDEN, "im.member.owner-not-removable", "不能移除群主");
        }
        if (ROLE_ADMIN.equals(target.getRole()) && !ROLE_OWNER.equals(operator.getRole())) {
            throw new BizException(ErrorCode.FORBIDDEN, "im.member.admin-remove-owner-only", "只有群主可以移除管理员");
        }
        memberMapper.deleteMember(conversationId, targetUserId);
        broadcaster.broadcastMemberChanged(conversationId, "REMOVE", targetUserId);
    }

    /**
     * 主动退群（群主不可退群）。
     *
     * @param conversationId 会话ID
     * @param userId         当前用户ID
     */
    @Transactional
    public void quit(Long conversationId, Long userId) {
        ImConversation conversation = guard.requireMember(conversationId, userId);
        requireGroup(conversation);
        ImConversationMember self = guard.requireMemberRecord(conversationId, userId);
        if (self != null && ROLE_OWNER.equals(self.getRole())) {
            throw new BizException(ErrorCode.FORBIDDEN, "im.member.owner-cannot-leave", "群主不能退群");
        }
        memberMapper.deleteMember(conversationId, userId);
        broadcaster.broadcastMemberChanged(conversationId, "QUIT", userId);
    }

    /**
     * 成员实体转视图对象。
     *
     * @param member 成员实体
     * @return 视图对象
     */
    public static MemberVO toVo(ImConversationMember member) {
        return MemberVO.builder()
                .userId(String.valueOf(member.getUserId()))
                .memberType(member.getMemberType())
                .role(member.getRole())
                .lastReadSeq(member.getLastReadSeq() == null ? 0L : member.getLastReadSeq())
                .muteUntil(format(member.getMuteUntil()))
                .joinTime(format(member.getJoinTime()))
                .build();
    }

    /**
     * 角色排序权重（群主 → 管理员 → 成员）。
     *
     * @param role 会话角色
     * @return 权重，越小越靠前
     */
    private static int roleRank(String role) {
        if (ROLE_OWNER.equals(role)) {
            return 0;
        }
        return ROLE_ADMIN.equals(role) ? 1 : 2;
    }

    /**
     * 校验会话为群聊。
     *
     * @param conversation 会话
     */
    private static void requireGroup(ImConversation conversation) {
        if (!TYPE_GROUP.equals(conversation.getType())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "im.member.group-only", "仅群聊支持成员管理");
        }
    }

    /**
     * 校验操作人为群主或管理员。
     *
     * @param conversationId 会话ID
     * @param operatorId     操作人ID
     * @return 操作人成员记录
     */
    private ImConversationMember requireManager(Long conversationId, Long operatorId) {
        ImConversationMember operator = guard.requireMemberRecord(conversationId, operatorId);
        if (operator == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "im.conversation.not-accessible", "会话不存在或无权访问");
        }
        if (!ROLE_OWNER.equals(operator.getRole()) && !ROLE_ADMIN.equals(operator.getRole())) {
            throw new BizException(ErrorCode.FORBIDDEN, "im.member.permission-denied", "仅群主或管理员可管理群成员");
        }
        return operator;
    }

    /**
     * 查询会话现有成员用户ID。
     *
     * @param conversationId 会话ID
     * @return 用户ID列表
     */
    private List<Long> existingUserIds(Long conversationId) {
        LambdaQueryWrapper<ImConversationMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(ImConversationMember::getUserId);
        wrapper.eq(ImConversationMember::getConversationId, conversationId);
        List<ImConversationMember> members = memberMapper.selectList(wrapper);
        List<Long> userIds = new ArrayList<>(members.size());
        for (ImConversationMember member : members) {
            userIds.add(member.getUserId());
        }
        return userIds;
    }

    /**
     * 归一化待添加成员：去空、去重、排除操作人。
     *
     * @param memberIds  原始成员ID
     * @param operatorId 操作人ID
     * @return 去重后的成员ID
     */
    private static Set<Long> normalizeMembers(List<Long> memberIds, Long operatorId) {
        Set<Long> unique = new LinkedHashSet<>();
        if (memberIds == null) {
            return unique;
        }
        for (Long memberId : memberIds) {
            if (memberId != null && !Objects.equals(memberId, operatorId)) {
                unique.add(memberId);
            }
        }
        return unique;
    }

    /**
     * 构造新成员实体。
     *
     * @param conversationId 会话ID
     * @param userId         用户ID
     * @return 成员实体
     */
    private static ImConversationMember newMember(Long conversationId, Long userId) {
        ImConversationMember member = new ImConversationMember();
        member.setConversationId(conversationId);
        member.setUserId(userId);
        member.setMemberType(MEMBER_USER);
        member.setRole(ROLE_MEMBER);
        member.setLastReadSeq(0L);
        member.setJoinTime(LocalDateTime.now());
        return member;
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
