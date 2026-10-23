package com.bluewhale.auth.domain.event;

/**
 * 用户领域事件（AUTH-17 / T-DDD-11）
 *
 * @param aggregateId 用户 ID
 * @param changeType 变更类型
 * @author BlueWhale
 */
public record UserDomainEvent(Long aggregateId, AuthDomainEvent.ChangeType changeType)
        implements AuthDomainEvent {
}
