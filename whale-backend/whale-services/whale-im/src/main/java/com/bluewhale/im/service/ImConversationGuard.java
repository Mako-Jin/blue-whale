package com.bluewhale.im.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.im.entity.ImConversation;
import com.bluewhale.im.entity.ImConversationMember;
import com.bluewhale.im.mapper.ImConversationMapper;
import com.bluewhale.im.mapper.ImConversationMemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 会话成员校验：统一收口「会话存在 + 当前用户是成员」判定。
 *
 * <p>所有会话与消息读写入口都必须先经过本类，避免各服务重复实现导致越权口径漂移。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class ImConversationGuard {

    private final ImConversationMapper conversationMapper;

    private final ImConversationMemberMapper memberMapper;

    /**
     * 校验会话存在且当前用户为成员。
     *
     * <p>会话不存在与无权访问返回同一提示，避免侧信道泄露会话是否存在。</p>
     *
     * @param conversationId 会话ID
     * @param userId         当前用户ID
     * @return 会话实体
     */
    public ImConversation requireMember(Long conversationId, Long userId) {
        ImConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "会话不存在或无权访问");
        }
        if (requireMemberRecord(conversationId, userId) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "会话不存在或无权访问");
        }
        return conversation;
    }

    /**
     * 读取当前用户的成员记录（校验成员身份并返回角色信息）。
     *
     * <p>不存在时返回 {@code null}，由调用方决定提示口径；群管理类操作需要拿到
     * {@code role} 判断权限，故单独提供本方法，避免重复查询。</p>
     *
     * @param conversationId 会话ID
     * @param userId         当前用户ID
     * @return 成员记录；非成员返回 {@code null}
     */
    public ImConversationMember requireMemberRecord(Long conversationId, Long userId) {
        LambdaQueryWrapper<ImConversationMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ImConversationMember::getConversationId, conversationId);
        wrapper.eq(ImConversationMember::getUserId, userId);
        return memberMapper.selectOne(wrapper);
    }
}
