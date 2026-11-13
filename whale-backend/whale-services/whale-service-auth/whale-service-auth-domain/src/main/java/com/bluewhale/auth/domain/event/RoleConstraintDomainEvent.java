package com.bluewhale.auth.domain.event;

/**
 * 角色约束聚合领域事件（权限中心扩展 M3）。
 *
 * <p>约束新建 / 更新 / 删除 / 启停时发布；约束校验失败本身为领域异常
 * （{@code RoleConstraintChecker} 抛出），不发事件。</p>
 *
 * @param constraintId 约束聚合主键
 * @param tenantId 租户 ID
 * @param changeType 变更类型
 * @param occurredAt 事件发生时间（epoch 毫秒）
 * @author BlueWhale
 */
public record RoleConstraintDomainEvent(Long constraintId, Long tenantId,
                                        ChangeType changeType, long occurredAt)
        implements AuthDomainEvent {

    /**
     * 获取聚合根标识
     *
     * @return 约束聚合主键
     */
    @Override
    public Long aggregateId() {
        return constraintId;
    }
}
