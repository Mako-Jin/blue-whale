package com.bluewhale.crm.marketing.domain.port;

import com.bluewhale.crm.marketing.domain.model.SendItemStatus;

/**
 * 消息投递结果。
 *
 * @param status 投递状态（SENT / SKIPPED / FAILED）
 * @param error 失败或跳过原因
 * @author BlueWhale
 */
public record MessageDispatchResult(SendItemStatus status, String error) {

    /**
     * 构造成功结果。
     *
     * @return 发送成功
     */
    public static MessageDispatchResult sent() {
        return new MessageDispatchResult(SendItemStatus.SENT, null);
    }

    /**
     * 构造跳过结果。
     *
     * @param reason 跳过原因
     * @return 已跳过
     */
    public static MessageDispatchResult skipped(String reason) {
        return new MessageDispatchResult(SendItemStatus.SKIPPED, reason);
    }

    /**
     * 构造失败结果。
     *
     * @param reason 失败原因
     * @return 发送失败
     */
    public static MessageDispatchResult failed(String reason) {
        return new MessageDispatchResult(SendItemStatus.FAILED, reason);
    }
}
