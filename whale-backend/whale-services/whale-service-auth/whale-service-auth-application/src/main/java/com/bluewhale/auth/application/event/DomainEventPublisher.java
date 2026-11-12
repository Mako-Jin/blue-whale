package com.bluewhale.auth.application.event;

import com.bluewhale.common.domain.event.DomainEvent;

/**
 * 领域事件发布端口（AUTH-17 / T-DDD-11）。
 *
 * <p>应用层定义端口、基础设施实现（六边形架构 / 依赖倒置，ADR-AUTH-004）。
 * 泛型为 common 的 {@link DomainEvent}（{@code AuthDomainEvent} 已继承该基类）；
 * 若未来多服务共用，本端口可整体下沉至 common-application，auth 无需改动。</p>
 *
 * @author BlueWhale
 */
public interface DomainEventPublisher {

    /**
     * 发布领域事件
     *
     * @param event 待发布的领域事件
     */
    void publish(DomainEvent event);
}
