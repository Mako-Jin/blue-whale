package com.bluewhale.auth.application.model;

/**
 * 用户配置条目（应用层模型）。
 *
 * @param tenantId    租户 ID
 * @param userId      用户 ID
 * @param configKey   配置键（前缀分域）
 * @param configValue 配置值（JSON）
 * @author BlueWhale
 */
public record UserConfigEntry(Long tenantId, Long userId, String configKey, String configValue) {
}
