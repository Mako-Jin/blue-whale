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

    /**
     * 异步投递一条已落库的消息。
     *
     * <p>后台线程无请求上下文，因此由调用方显式传入租户与链路信息；
     * 线程内以条件更新抢占 {@code PENDING → DISPATCHING}，抢占失败说明已被
     * 其它线程或实例处理，直接跳过，保证不重复投递。</p>
     *
     * @param messageId 消息ID
     * @param tenantId  消息所属租户
     * @param traceId   发送请求链路追踪ID（可为 {@code null}）
     */
    void dispatchAsync(Long messageId, Long tenantId, String traceId);
}
