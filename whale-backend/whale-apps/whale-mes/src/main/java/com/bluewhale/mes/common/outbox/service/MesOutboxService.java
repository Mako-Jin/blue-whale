package com.bluewhale.mes.common.outbox.service;

import java.time.LocalDateTime;

/**
 * MES 集成发件箱服务
 *
 * <p>MES 向 ERP 等外部系统回传业务事件时，先落本地发件箱再异步投递，
 * 保证「本地事务提交」与「外部通知」最终一致（REQ-ARCH-021）。</p>
 *
 * @author BlueWhale
 */
public interface MesOutboxService {

    /**
     * 写入一条待投递事件（同一源事件幂等键重复写入不会造成重复投递）。
     *
     * @param sourceEventId 源事件幂等键
     * @param eventType     事件类型，如 {@code mes.work-order.completed}
     * @param aggregateType 聚合类型，如 {@code work-order}
     * @param aggregateId   聚合 ID
     * @param payload       事件报文（JSON）
     */
    void enqueue(String sourceEventId, String eventType, String aggregateType, String aggregateId, String payload);

    /**
     * 标记事件已投递成功。
     *
     * @param id 发件箱记录主键
     */
    void markSent(Long id);

    /**
     * 标记事件投递失败，并登记下次重试时间。
     *
     * @param id            发件箱记录主键
     * @param retryCount    已重试次数
     * @param nextRetryTime 下次重试时间
     * @param error         最后错误摘要
     */
    void markFailed(Long id, int retryCount, LocalDateTime nextRetryTime, String error);
}
