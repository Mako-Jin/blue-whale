package com.bluewhale.auth.domain.event;

/**
 * 用户身份档案聚合领域事件（权限中心扩展 M3）。
 *
 * <p>档案新建 / 更新（含子表整体替换）/ 删除 / 联系方式验证通过时发布；不携带证件号、
 * 密保答案等敏感载荷。</p>
 *
 * @param profileId 档案聚合主键
 * @param tenantId 租户 ID
 * @param userId 用户 ID
 * @param changeType 变更类型
 * @param occurredAt 事件发生时间（epoch 毫秒）
 * @author BlueWhale
 */
public record UserProfileDomainEvent(Long profileId, Long tenantId, Long userId,
                                     ChangeType changeType, long occurredAt) implements AuthDomainEvent {

    /**
     * 获取聚合根标识
     *
     * @return 档案聚合主键
     */
    @Override
    public Long aggregateId() {
        return profileId;
    }
}
