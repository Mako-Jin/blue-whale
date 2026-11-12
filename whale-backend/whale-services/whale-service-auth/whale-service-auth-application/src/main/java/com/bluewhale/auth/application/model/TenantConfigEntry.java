package com.bluewhale.auth.application.model;

/**
 * 租户配置条目（应用层模型）。
 *
 * @param tenantId    租户 ID
 * @param configKey   配置键（前缀分域）
 * @param configValue 配置值（JSON），作为该租户用户的默认值层
 * @author BlueWhale
 */
public record TenantConfigEntry(Long tenantId, String configKey, String configValue) {
}
