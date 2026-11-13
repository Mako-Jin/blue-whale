package com.bluewhale.auth.domain.event;

/**
 * 会话领域事件（权限中心扩展 M3）。
 *
 * <p>强制下线（SessionRevoked）与设备信任变更（DeviceTrusted）时发布；登录历史为
 * append-only 账本，不发布事件。</p>
 *
 * @param jti 会话令牌唯一标识（强制下线时非空；设备信任变更时为 {@code null}）
 * @param deviceId 设备主键（设备信任变更时非空）
 * @param tenantId 租户 ID
 * @param userId 用户 ID
 * @param changeType 变更类型（SESSION_REVOKED / DEVICE_TRUSTED 等）
 * @param occurredAt 事件发生时间（epoch 毫秒）
 * @author BlueWhale
 */
public record SessionDomainEvent(String jti, Long deviceId, Long tenantId, Long userId,
                                 ChangeType changeType, long occurredAt) implements AuthDomainEvent {

    /**
     * 获取聚合根标识（会话无独立聚合主键，取设备主键或 {@code null}）
     *
     * @return 设备主键；无设备上下文时返回 {@code null}
     */
    @Override
    public Long aggregateId() {
        return deviceId;
    }
}
