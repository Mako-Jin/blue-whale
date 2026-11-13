package com.bluewhale.auth.domain.event;

/**
 * 权限策略聚合领域事件（权限中心扩展 M3）。
 *
 * <p>策略发布（新建 / 更新 / 启停 / 设默认）时发布，供权限缓存失效与审计消费。</p>
 *
 * @param strategyId 策略聚合主键
 * @param tenantId 租户 ID
 * @param changeType 变更类型
 * @param occurredAt 事件发生时间（epoch 毫秒）
 * @author BlueWhale
 */
public record PermissionStrategyDomainEvent(Long strategyId, Long tenantId,
                                            ChangeType changeType, long occurredAt)
        implements AuthDomainEvent {

    /**
     * 获取聚合根标识
     *
     * @return 策略聚合主键
     */
    @Override
    public Long aggregateId() {
        return strategyId;
    }
}
