package com.bluewhale.auth.application.model.command;

/**
 * 三方授权起始命令（权限中心扩展 M2）：登录意图的授权入口。
 *
 * @param providerCode 渠道编码（dingtalk/feishu/wecom）
 * @param tenantCode 登录目标租户编码
 * @param redirect 登录完成后的前端回跳相对路径（必须以 {@code /} 开头）
 * @author BlueWhale
 */
public record SocialAuthorizeCommand(String providerCode, String tenantCode, String redirect) {
}
