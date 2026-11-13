package com.bluewhale.auth.application.model.command;

/**
 * 三方解绑命令（权限中心扩展 M2）。
 *
 * @param providerCode 渠道编码
 * @author BlueWhale
 */
public record SocialUnbindCommand(String providerCode) {
}
