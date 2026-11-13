package com.bluewhale.auth.domain.event;

/**
 * 角色继承关系领域事件（权限中心扩展 M3）。
 *
 * <p>角色挂载 / 解除父角色、继承策略变更时发布（{@code RoleHierarchyChanged} 语义）。</p>
 *
 * @param roleId 角色聚合主键
 * @param tenantId 租户 ID
 * @param changeType 变更类型
 * @param occurredAt 事件发生时间（epoch 毫秒）
 * @author BlueWhale
 */
public record RoleHierarchyDomainEvent(Long roleId, Long tenantId,
                                       ChangeType changeType, long occurredAt)
        implements AuthDomainEvent {

    /**
     * 获取聚合根标识
     *
     * @return 角色聚合主键
     */
    @Override
    public Long aggregateId() {
        return roleId;
    }
}
