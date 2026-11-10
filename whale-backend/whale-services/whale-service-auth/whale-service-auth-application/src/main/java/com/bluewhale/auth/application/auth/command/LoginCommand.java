package com.bluewhale.auth.application.auth.command;

/**
 * 登录命令。
 *
 * @param tenantCode 租户编码（可空，为空时按现有规则解析默认租户）
 * @param username 用户名
 * @param password 明文密码
 * @author BlueWhale
 */
public record LoginCommand(String tenantCode, String username, String password) {
}
