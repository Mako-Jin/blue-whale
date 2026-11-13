package com.bluewhale.auth.application.model.command;

/**
 * 一次性登录码换令牌命令（权限中心扩展 M2）。
 *
 * @param loginCode 回调 302 带给前端的一次性登录码
 * @author BlueWhale
 */
public record SocialLoginByCodeCommand(String loginCode) {
}
