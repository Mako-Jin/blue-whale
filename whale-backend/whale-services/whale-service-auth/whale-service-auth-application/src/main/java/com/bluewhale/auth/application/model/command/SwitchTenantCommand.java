package com.bluewhale.auth.application.model.command;

/**
 * 切换租户命令。
 *
 * @param authorization 请求头原始值（Bearer 前缀，可空）
 * @param targetTenantId 目标租户 ID
 * @param currentRefreshToken Cookie 中解析出的当前 refreshToken（可空）
 * @author BlueWhale
 */
public record SwitchTenantCommand(String authorization, Long targetTenantId, String currentRefreshToken) {
}
