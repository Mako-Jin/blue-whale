package com.bluewhale.common.domain.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 领域事件抽象基类：为类式事件统一提供事件 ID、发生时间与事件类型。
 *
 * <p>{@code record} 式事件无需继承本类，直接实现 {@link DomainEvent} 并携带自身载荷即可。</p>
 *
 * @author BlueWhale
 */
public abstract class AbstractDomainEvent implements DomainEvent {

    /** 序列化版本号 */
    private static final long serialVersionUID = 1L;

    /** 事件唯一标识 */
    private final String eventId = UUID.randomUUID().toString();

    /** 事件发生时间 */
    private final LocalDateTime occurredOn = LocalDateTime.now();

    /**
     * 获取事件唯一标识
     *
     * @return 事件唯一标识
     */
    public String getEventId() {
        return eventId;
    }

    /**
     * 获取事件发生时间
     *
     * @return 事件发生时间
     */
    public LocalDateTime getOccurredOn() {
        return occurredOn;
    }

    /**
     * 获取事件类型：默认取实现类的简单类名
     *
     * @return 事件类型
     */
    public String getEventType() {
        return getClass().getSimpleName();
    }
}
