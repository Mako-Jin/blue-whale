package com.bluewhale.crm.service.domain.repository;

import com.bluewhale.crm.service.domain.model.ReturnOutboxEvent;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 退换货下发 Outbox 仓储端口（对应 {@code crm_common_outbox}）。
 *
 * @author BlueWhale
 */
public interface ReturnOutboxRepository {

    /**
     * 写入待下发事件（同事务落库）。
     *
     * @param requestId 退换货申请 ID
     * @param payload 事件负载（JSON）
     * @param nextRetryAt 下次可下发时间
     */
    void enqueue(Long requestId, String payload, LocalDateTime nextRetryAt);

    /**
     * 查询到期的待下发事件（全租户，供后台任务）。
     *
     * @param limit 最大条数
     * @param now 当前时间
     * @return 事件列表
     */
    List<ReturnOutboxEvent> findPending(int limit, LocalDateTime now);

    /**
     * 标记事件已发送。
     *
     * @param id 事件 ID
     * @return 更新成功返回 true
     */
    boolean markSent(Long id);

    /**
     * 标记事件失败并安排退避重试。
     *
     * @param id 事件 ID
     * @param error 失败原因
     * @param nextRetryAt 下次重试时间
     * @return 更新成功返回 true
     */
    boolean markFailed(Long id, String error, LocalDateTime nextRetryAt);

    /**
     * 重置指定申请的事件为待重试。
     *
     * @param requestId 退换货申请 ID
     * @param nextRetryAt 下次重试时间
     */
    void resetForRetry(Long requestId, LocalDateTime nextRetryAt);

    /**
     * 关闭指定申请的待下发事件。
     *
     * @param requestId 退换货申请 ID
     */
    void cancelByRequestId(Long requestId);
}
