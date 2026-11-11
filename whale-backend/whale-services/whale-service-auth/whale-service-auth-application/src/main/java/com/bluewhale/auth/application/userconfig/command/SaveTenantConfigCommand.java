package com.bluewhale.auth.application.userconfig.command;

/**
 * 保存租户配置命令。
 *
 * @param configKey   配置键
 * @param configValue 配置值（JSON）
 * @param updatedBy   最后更新人用户 ID
 * @author BlueWhale
 */
public record SaveTenantConfigCommand(String configKey, String configValue, Long updatedBy) {
}
