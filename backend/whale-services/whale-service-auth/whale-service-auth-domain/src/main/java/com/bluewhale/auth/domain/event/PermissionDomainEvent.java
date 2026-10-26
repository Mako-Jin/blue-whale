package com.bluewhale.auth.domain.event;

/**
 * 权限点领域事件（AUTH-17 / T-DDD-11）
 *
 * @param aggregateId 权限点 ID
 * @param changeType 变更类型
 * @author BlueWhale
 */
public record PermissionDomainEvent(Long aggregateId, AuthDomainEvent.ChangeType changeType)
        implements AuthDomainEvent {
}
