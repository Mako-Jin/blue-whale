package com.bluewhale.auth.domain.event;

/**
 * 菜单领域事件（AUTH-17 / T-DDD-11）
 *
 * @param aggregateId 菜单 ID
 * @param changeType 变更类型
 * @author BlueWhale
 */
public record MenuDomainEvent(Long aggregateId, AuthDomainEvent.ChangeType changeType)
        implements AuthDomainEvent {
}
