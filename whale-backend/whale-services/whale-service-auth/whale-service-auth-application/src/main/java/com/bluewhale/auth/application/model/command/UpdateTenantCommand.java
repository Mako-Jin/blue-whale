package com.bluewhale.auth.application.model.command;

/**
 * 租户更新命令：名称与配额的全量/局部更新。
 *
 * @param tenantName 新租户名称（可空表示不变）
 * @param quotaJson 新配额 JSON（可空表示不变）
 * @author BlueWhale
 */
public record UpdateTenantCommand(String tenantName, String quotaJson) {
}
