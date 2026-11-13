package com.bluewhale.auth.domain.event;

/**
 * 三方绑定聚合领域事件（权限中心扩展 M2）。
 *
 * <p>绑定建立 / 解绑 / 资料刷新时发布，供审计与缓存消费；不携带三方敏感信息
 * （unionId、头像等不入事件载荷）。</p>
 *
 * @param provider 渠道编码
 * @param bindingId 绑定聚合主键
 * @param tenantId 用户归属租户 ID
 * @param userId 绑定用户 ID
 * @param changeType 变更类型
 * @param occurredAt 事件发生时间（epoch 毫秒）
 * @author BlueWhale
 */
public record SocialBindingDomainEvent(String provider, Long bindingId, Long tenantId, Long userId,
                                       ChangeType changeType, long occurredAt) implements AuthDomainEvent {

    /**
     * 获取聚合根标识
     *
     * @return 绑定聚合主键
     */
    @Override
    public Long aggregateId() {
        return bindingId;
    }
}
