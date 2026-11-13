package com.bluewhale.auth.application.model.command;

/**
 * 三方绑定授权起始命令（权限中心扩展 M2）：已登录用户为当前账号发起绑定。
 *
 * @param providerCode 渠道编码
 * @param redirect 绑定完成后的前端回跳相对路径（必须以 {@code /} 开头）
 * @author BlueWhale
 */
public record SocialBindAuthorizeCommand(String providerCode, String redirect) {
}
