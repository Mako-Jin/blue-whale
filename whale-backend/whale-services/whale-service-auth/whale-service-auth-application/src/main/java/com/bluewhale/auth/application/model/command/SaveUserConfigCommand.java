package com.bluewhale.auth.application.model.command;

/**
 * 保存用户配置命令。
 *
 * @param configKey   配置键
 * @param configValue 配置值（JSON）
 * @param updatedBy   最后更新人用户 ID
 * @author BlueWhale
 */
public record SaveUserConfigCommand(String configKey, String configValue, Long updatedBy) {
}
