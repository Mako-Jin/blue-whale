package com.bluewhale.im.ws;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.im.entity.ImConversationMember;
import com.bluewhale.im.mapper.ImConversationMemberMapper;
import com.bluewhale.im.vo.MessageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 新消息 WS 广播器。
 *
 * <p>消息落库后按会话成员推送 {@code message.new} 帧；推送失败仅记录日志，
 * 数据库始终是唯一事实源，客户端可通过 {@code afterSeq} 补拉兜底。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImMessageBroadcaster {

    private final ImConversationMemberMapper memberMapper;

    private final WsSessionRegistry registry;

    /**
     * 向会话全部成员推送新消息。
     *
     * @param conversationId 会话ID
     * @param message        消息视图对象
     */
    public void broadcast(Long conversationId, MessageVO message) {
        try {
            List<Long> memberIds = memberIds(conversationId);
            if (memberIds.isEmpty()) {
                return;
            }
            registry.send(memberIds, WsFrames.messageNew(message));
            log.debug("消息已推送: conversationId={}, seq={}, onlineMembers={}",
                    conversationId, message.getSeq(), memberIds.size());
        } catch (RuntimeException e) {
            log.warn("消息推送失败（已落库，不影响发送）: conversationId={}", conversationId, e);
        }
    }

    /**
     * 向会话全部成员推送任意帧（机器人流式帧等）。
     *
     * @param conversationId 会话ID
     * @param frame          待推送帧
     */
    public void broadcastFrame(Long conversationId, Map<String, Object> frame) {
        try {
            List<Long> memberIds = memberIds(conversationId);
            if (memberIds.isEmpty()) {
                return;
            }
            registry.send(memberIds, frame);
        } catch (RuntimeException e) {
            log.warn("帧推送失败: conversationId={}, type={}", conversationId, frame.get("type"), e);
        }
    }

    /**
     * 向会话全部成员推送撤回通知。
     *
     * @param conversationId 会话ID
     * @param messageId      被撤回消息ID
     * @param seq            被撤回消息序号
     * @param operatorId     操作人ID
     */
    public void broadcastRecalled(Long conversationId, Long messageId, Long seq, Long operatorId) {
        try {
            List<Long> memberIds = memberIds(conversationId);
            if (memberIds.isEmpty()) {
                return;
            }
            registry.send(memberIds, WsFrames.messageRecalled(conversationId, messageId, seq, operatorId));
            log.debug("撤回通知已推送: conversationId={}, messageId={}", conversationId, messageId);
        } catch (RuntimeException e) {
            log.warn("撤回通知推送失败（撤回已生效，客户端补拉可见）: conversationId={}", conversationId, e);
        }
    }

    /**
     * 查询会话成员用户ID。
     *
     * @param conversationId 会话ID
     * @return 成员用户ID列表
     */
    private List<Long> memberIds(Long conversationId) {
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
}
