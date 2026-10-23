package com.bluewhale.auth.domain.event;

/**
 * 部门领域事件（AUTH-17 / T-DDD-11）
 *
 * @param aggregateId 部门 ID
 * @param changeType 变更类型
 * @author BlueWhale
 */
public record DeptDomainEvent(Long aggregateId, AuthDomainEvent.ChangeType changeType)
        implements AuthDomainEvent {
}
