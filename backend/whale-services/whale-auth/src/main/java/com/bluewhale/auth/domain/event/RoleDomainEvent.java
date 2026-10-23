package com.bluewhale.auth.domain.event;

/**
 * 角色领域事件（AUTH-17 / T-DDD-11）
 *
 * @param aggregateId 角色 ID
 * @param changeType 变更类型
 * @author BlueWhale
 */
public record RoleDomainEvent(Long aggregateId, AuthDomainEvent.ChangeType changeType)
        implements AuthDomainEvent {
}
