package com.bluewhale.im.service;

import com.bluewhale.im.dto.InternalNotifyRequest;
import com.bluewhale.im.vo.ConversationVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 内网站内通知服务：把业务提醒投递到接收人的「蓝鲸助手」机器人会话。
 *
 * <p>逐人投递、单点失败只告警不阻断；同一请求复用 {@code clientMsgId}，会话内重复提交幂等。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImNotifyService {

    /** 通知正文最大长度（避免超长消息拖慢会话列表渲染） */
    private static final int MAX_CONTENT_LENGTH = 1000;

    /** 服务端生成的幂等键前缀 */
    private static final String GENERATED_ID_PREFIX = "un-notify-";

    private final ImBotService botService;

    private final ImMessageService messageService;

    /**
     * 批量投递内网站内通知。
     *
     * @param request 通知请求
     * @return 成功投递人数
     */
    public int notify(InternalNotifyRequest request) {
        List<Long> recipients = distinct(request.getUserIds());
        if (recipients.isEmpty()) {
            return 0;
        }
        String content = compose(request.getTitle(), request.getContent());
        String clientMsgId = resolveClientMsgId(request.getClientMsgId());
        int delivered = 0;
        for (Long userId : recipients) {
            try {
                ConversationVO conversation = botService.openDefault(userId);
                Long conversationId = Long.valueOf(conversation.getId());
                messageService.sendAsBot(conversationId, botService.botUserId(), content, clientMsgId);
                delivered++;
            } catch (RuntimeException e) {
                log.warn("站内通知投递失败: userId={}, bizType={}, bizId={}, error={}",
                        userId, request.getBizType(), request.getBizId(), e.getMessage());
            }
        }
        log.info("站内通知已投递: bizType={}, bizId={}, total={}, delivered={}",
                request.getBizType(), request.getBizId(), recipients.size(), delivered);
        return delivered;
    }

    /**
     * 去重并保序过滤空值。
     *
     * @param userIds 接收人ID列表
     * @return 去重后的接收人ID列表
     */
    private List<Long> distinct(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        Set<Long> unique = new LinkedHashSet<>();
        for (Long userId : userIds) {
            if (userId != null) {
                unique.add(userId);
            }
        }
        return new ArrayList<>(unique);
    }

    /**
     * 拼装通知正文：标题与正文以换行分隔，并收敛到最大长度。
     *
     * @param title   通知标题
     * @param content 通知正文
     * @return 拼装后的正文
     */
    private String compose(String title, String content) {
        String text = content == null || content.isBlank() ? title : title + "\n" + content;
        return text.length() <= MAX_CONTENT_LENGTH ? text : text.substring(0, MAX_CONTENT_LENGTH);
    }

    /**
     * 解析幂等键：请求未提供时生成一次性键。
     *
     * @param clientMsgId 请求携带的幂等键
     * @return 幂等键
     */
    private String resolveClientMsgId(String clientMsgId) {
        return clientMsgId == null || clientMsgId.isBlank()
                ? GENERATED_ID_PREFIX + System.nanoTime()
                : clientMsgId;
    }
}
