package com.bluewhale.oa.integration.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 集成事件 Outbox 聚合。
 *
 * <p>本地业务事务内只落库，提交后由调度器异步推送；失败按退避时间重试，
 * 超过最大次数转为死信。{@code eventKey} 在租户内唯一，保证同一业务事件不重复发送。</p>
 *
 * @author BlueWhale
 */
@Data
public class OutboxEvent {

    /** 最大重试次数 */
    private static final int MAX_RETRY = 5;

    /** 每次重试的退避基数（分钟） */
    private static final long BACKOFF_BASE_MINUTES = 5L;

    /** 主键 */
    private Long id;

    /** 事件幂等键 */
    private String eventKey;

    /** 事件类型 */
    private String eventType;

    /** 聚合类型 */
    private String aggregateType;

    /** 聚合ID */
    private String aggregateId;

    /** 事件体（JSON） */
    private String payload;

    /** 状态 */
    private OutboxStatus status;

    /** 重试次数 */
    private int retryCount;

    /** 下次重试时间 */
    private LocalDateTime nextRetryTime;

    /** 发送成功时间 */
    private LocalDateTime sentAt;

    /** 失败原因 */
    private String errorMsg;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 标记发送成功。
     */
    public void markSent() {
        status = OutboxStatus.SENT;
        sentAt = LocalDateTime.now();
        nextRetryTime = null;
        errorMsg = null;
    }

    /**
     * 标记发送失败，按重试次数计算退避或转死信。
     *
     * @param error 失败原因
     */
    public void markFailed(String error) {
        if (status == OutboxStatus.SENT) {
            return;
        }
        retryCount++;
        errorMsg = error == null ? null : error.substring(0, Math.min(error.length(), 512));
        if (retryCount >= MAX_RETRY) {
            status = OutboxStatus.DEAD;
            nextRetryTime = null;
            return;
        }
        status = OutboxStatus.FAILED;
        nextRetryTime = LocalDateTime.now().plusMinutes(BACKOFF_BASE_MINUTES * retryCount);
    }
}
