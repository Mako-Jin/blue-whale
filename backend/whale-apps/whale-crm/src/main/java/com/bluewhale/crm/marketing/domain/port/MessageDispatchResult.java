package com.bluewhale.crm.marketing.domain.port;

import com.bluewhale.crm.marketing.domain.model.SendItemStatus;

/**
 * 消息投递结果。
 *
 * <p>{@code messageId} / {@code providerMessageId} 用于后续承接渠道送达回执：
 * 消息服务按渠道侧消息标识匹配投递记录并回调，CRM 侧用 {@code messageId} 定位触达明细。</p>
 *
 * @param status 投递状态（SENT / SKIPPED / FAILED）
 * @param error 失败或跳过原因
 * @param messageId 消息服务消息ID（投递成功时回填）
 * @param providerMessageId 渠道侧消息标识（投递成功时回填）
 * @author BlueWhale
 */
public record MessageDispatchResult(SendItemStatus status, String error, Long messageId, String providerMessageId) {

    /**
     * 构造成功结果。
     *
     * @return 发送成功
     */
    public static MessageDispatchResult sent() {
        return new MessageDispatchResult(SendItemStatus.SENT, null, null, null);
    }

    /**
     * 构造带消息标识的成功结果。
     *
     * @param messageId 消息服务消息ID
     * @param providerMessageId 渠道侧消息标识
     * @return 发送成功
     */
    public static MessageDispatchResult sent(Long messageId, String providerMessageId) {
        return new MessageDispatchResult(SendItemStatus.SENT, null, messageId, providerMessageId);
    }

    /**
     * 构造跳过结果。
     *
     * @param reason 跳过原因
     * @return 已跳过
     */
    public static MessageDispatchResult skipped(String reason) {
        return new MessageDispatchResult(SendItemStatus.SKIPPED, reason, null, null);
    }

    /**
     * 构造失败结果。
     *
     * @param reason 失败原因
     * @return 发送失败
     */
    public static MessageDispatchResult failed(String reason) {
        return new MessageDispatchResult(SendItemStatus.FAILED, reason, null, null);
    }
}
