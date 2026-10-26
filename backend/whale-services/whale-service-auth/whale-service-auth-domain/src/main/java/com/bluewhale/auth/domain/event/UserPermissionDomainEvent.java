package com.bluewhale.auth.domain.event;

/**
 * 用户 ABAC 属性领域事件（AUTH-17 / T-DDD-11）
 *
 * @param aggregateId 用户 ID
 * @param changeType 变更类型
 * @author BlueWhale
 */
public record UserPermissionDomainEvent(Long aggregateId, AuthDomainEvent.ChangeType changeType)
        implements AuthDomainEvent {
}
