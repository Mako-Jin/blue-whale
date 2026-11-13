package com.bluewhale.auth.domain.event;

/**
 * 用户组聚合领域事件（权限中心扩展 M3）。
 *
 * <p>用户组新建 / 更新 / 删除 / 成员关系变更（含动态规则重算快照）时发布。</p>
 *
 * @param groupId 用户组聚合主键
 * @param tenantId 租户 ID
 * @param changeType 变更类型
 * @param occurredAt 事件发生时间（epoch 毫秒）
 * @author BlueWhale
 */
public record UserGroupDomainEvent(Long groupId, Long tenantId,
                                   ChangeType changeType, long occurredAt) implements AuthDomainEvent {

    /**
     * 获取聚合根标识
     *
     * @return 用户组聚合主键
     */
    @Override
    public Long aggregateId() {
        return groupId;
    }
}
