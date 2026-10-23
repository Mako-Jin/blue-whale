package com.bluewhale.auth.application.event;

import com.bluewhale.auth.domain.event.AuthDomainEvent;

/**
 * 领域事件发布端口（AUTH-17 / T-DDD-11）。
 *
 * <p>应用层只依赖本端口，由基础设施层提供 Spring 事件实现，避免应用层反向依赖框架。</p>
 *
 * @author BlueWhale
 */
public interface DomainEventPublisher {

    /**
     * 发布领域事件
     *
     * @param event 待发布的领域事件
     */
    void publish(AuthDomainEvent event);
}
