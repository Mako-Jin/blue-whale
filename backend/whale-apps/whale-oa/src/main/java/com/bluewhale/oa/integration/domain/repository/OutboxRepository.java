package com.bluewhale.oa.integration.domain.repository;

import com.bluewhale.oa.integration.domain.model.OutboxEvent;

import java.util.List;
import java.util.Optional;

/**
 * 集成事件 Outbox 仓储。
 *
 * @author BlueWhale
 */
public interface OutboxRepository {

    /**
     * 新增事件；事件键已存在时不重复写入（幂等）。
     *
     * @param event 事件
     * @return 新增返回 {@code true}，已存在返回 {@code false}
     */
    boolean insertIfAbsent(OutboxEvent event);

    /**
     * 按主键查询事件。
     *
     * @param id 主键
     * @return 事件
     */
    Optional<OutboxEvent> findById(Long id);

    /**
     * 按事件键查询事件。
     *
     * @param eventKey 事件键
     * @return 事件
     */
    Optional<OutboxEvent> findByEventKey(String eventKey);

    /**
     * 查询待发送或到期待重试的事件。
     *
     * @param limit 最大条数
     * @return 事件列表
     */
    List<OutboxEvent> listRetryable(int limit);

    /**
     * 更新事件状态。
     *
     * @param event 事件
     */
    void update(OutboxEvent event);
}
