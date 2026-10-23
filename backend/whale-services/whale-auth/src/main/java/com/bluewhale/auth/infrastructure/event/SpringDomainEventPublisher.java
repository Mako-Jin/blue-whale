package com.bluewhale.auth.infrastructure.event;

import com.bluewhale.auth.application.event.DomainEventPublisher;
import com.bluewhale.auth.domain.event.AuthDomainEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 基于 Spring 事件的领域事件发布适配器（AUTH-17 / T-DDD-11）。
 *
 * <p>将应用层的 {@link DomainEventPublisher} 端口桥接到
 * {@link ApplicationEventPublisher}，使缓存失效与审计监听等基础设施动作解耦。</p>
 *
 * @author BlueWhale
 */
@Component
public class SpringDomainEventPublisher implements DomainEventPublisher {

    /** Spring 事件发布器 */
    private final ApplicationEventPublisher publisher;

    /**
     * 构造器注入
     *
     * @param publisher Spring 事件发布器
     */
    public SpringDomainEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(AuthDomainEvent event) {
        publisher.publishEvent(event);
    }
}
