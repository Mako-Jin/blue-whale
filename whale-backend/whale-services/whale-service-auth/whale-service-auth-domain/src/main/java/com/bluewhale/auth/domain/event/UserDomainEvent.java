package com.bluewhale.auth.domain.event;

/**
 * 用户领域事件（AUTH-17 / T-DDD-11）
 *
 * @param aggregateId 用户 ID
 * @param changeType 变更类型
 * @param tenantId 受影响租户 ID；为空时按事件处理时的当前租户失效
 * @author BlueWhale
 */
public record UserDomainEvent(Long aggregateId, AuthDomainEvent.ChangeType changeType, Long tenantId)
        implements AuthDomainEvent {

    /**
     * 兼容无显式租户的事件（按当前租户上下文失效）
     *
     * @param aggregateId 用户 ID
     * @param changeType 变更类型
     */
    public UserDomainEvent(Long aggregateId, AuthDomainEvent.ChangeType changeType) {
        this(aggregateId, changeType, null);
    }
}
