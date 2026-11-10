package com.bluewhale.auth.application.tenant.command;

/**
 * 租户启停命令。
 *
 * @param status 目标状态（1 启用 / 0 停用）
 * @author BlueWhale
 */
public record ChangeTenantStatusCommand(Integer status) {
}
