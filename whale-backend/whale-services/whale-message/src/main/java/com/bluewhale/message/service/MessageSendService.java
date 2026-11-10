package com.bluewhale.message.service;

import com.bluewhale.message.dto.SendMessageRequest;
import com.bluewhale.message.vo.SendMessageResult;

/**
 * 消息发送服务。
 *
 * @author BlueWhale
 */
public interface MessageSendService {

    /**
     * 发送一条逻辑消息到指定渠道。
     *
     * @param request 发送请求
     * @return 发送结果（含幂等命中标记与投递明细）
     */
    SendMessageResult send(SendMessageRequest request);

    /**
     * 重试一条消息中失败的投递。
     *
     * <p>只重试 {@code FAILED} 投递，不重发已成功/已跳过/已 mock 的记录；
     * mock 模式下一律拒绝，避免把“未真实发送”误记为恢复成功。</p>
     *
     * @param messageId 消息ID
     * @return 重试后的消息状态与投递明细
     */
    SendMessageResult retry(Long messageId);
}
