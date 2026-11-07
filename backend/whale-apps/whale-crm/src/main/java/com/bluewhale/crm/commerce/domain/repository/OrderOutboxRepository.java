package com.bluewhale.crm.commerce.domain.repository;

import com.bluewhale.crm.commerce.domain.model.OrderOutboxEvent;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商务订单下发 Outbox 仓储端口（对应 {@code crm_common_outbox}）。
 *
 * @author BlueWhale
 */
public interface OrderOutboxRepository {

    /**
     * 写入待下发事件（同事务落库）。
     *
     * @param orderId 订单 ID
     * @param payload 事件负载（JSON）
     * @param nextRetryAt 下次可下发时间
     */
    void enqueue(Long orderId, String payload, LocalDateTime nextRetryAt);

    /**
     * 查询到期的待下发事件（全租户，供后台任务）。
     *
     * @param limit 最大条数
     * @param now 当前时间
     * @return 事件列表
     */
    List<OrderOutboxEvent> findPending(int limit, LocalDateTime now);

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
     * 重置指定订单的事件为待重试。
     *
     * @param orderId 订单 ID
     * @param nextRetryAt 下次重试时间
     */
    void resetForRetry(Long orderId, LocalDateTime nextRetryAt);

    /**
     * 关闭指定订单的待下发事件（取消订单时调用）。
     *
     * @param orderId 订单 ID
     */
    void cancelByOrderId(Long orderId);
}
