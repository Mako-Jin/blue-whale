package com.bluewhale.common.domain.event.publisher;

import com.bluewhale.common.domain.event.DomainEvent;

import java.util.Collection;

/**
 * 领域事件发布端口：由基础设施层（Spring 事件、MQ 等）实现，领域与应用层仅依赖本接口。
 *
 * @author BlueWhale
 */
@FunctionalInterface
public interface DomainEventPublisher {

    /**
     * 发布单条领域事件
     *
     * @param event 领域事件
     */
    void publish(DomainEvent event);

    /**
     * 批量发布领域事件：默认逐条委托 {@link #publish(DomainEvent)}，实现方可按需覆盖以支持批量投递
     *
     * @param events 领域事件集合，可为 {@code null} 或空
     */
    default void publishAll(Collection<? extends DomainEvent> events) {
        if (events == null) {
            return;
        }
        events.forEach(this::publish);
    }
}
