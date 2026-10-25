package com.bluewhale.auth.application.tenant.command;

import java.time.LocalDateTime;

/**
 * 租户开通命令：创建租户并初始化管理员、默认角色、菜单与部门根。
 *
 * @param tenantCode 租户编码（全局唯一）
 * @param tenantName 租户名称
 * @param expireTime 到期时间（{@code null} 表示长期有效）
 * @param quotaJson 配额 JSON（可空）
 * @param adminUsername 初始管理员用户名（租户内唯一）
 * @param rawPassword 初始管理员明文口令（落库前 BCrypt 加密）
 * @param adminNickname 初始管理员昵称（可空）
 * @author BlueWhale
 */
public record CreateTenantCommand(String tenantCode, String tenantName, LocalDateTime expireTime,
                                  String quotaJson, String adminUsername, String rawPassword,
                                  String adminNickname) {
}
