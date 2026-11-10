package com.bluewhale.im.service;

import com.bluewhale.im.dto.SendMessageRequest;
import com.bluewhale.im.entity.ImMessage;
import com.bluewhale.im.vo.MessageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 发送消息应用服务：落库、触发机器人、装配视图对象。
 *
 * <p>把「发送 + 机器人钩子 + 响应装配」收敛在服务层，controller 不再直接依赖
 * {@link ImMessage} 实体（T-18 分层约束）。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class ImMessageSendService {

    private final ImMessageService messageService;

    private final ImAgentChatService agentChatService;

    /**
     * 发送用户消息并按需触发机器人回复。
     *
     * @param conversationId  会话ID
     * @param userId          当前用户ID
     * @param request         发送请求（{@code clientMsgId} 幂等）
     * @param permissionCodes 网关注入的权限码（机器人工具调用放行用）
     * @return 消息视图
     */
    public MessageVO send(Long conversationId, Long userId, SendMessageRequest request, String permissionCodes) {
        ImMessage message = messageService.send(conversationId, userId, request);
        agentChatService.onUserMessage(message, permissionCodes);
        return ImMessageService.toVo(message);
    }
}
