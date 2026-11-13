package com.bluewhale.auth.application.model.command;

/**
 * 三方回调命令（权限中心扩展 M2）：渠道重定向回服务侧回调端点时携带的参数。
 *
 * @param providerCode 渠道编码
 * @param code 渠道授权码（用户拒绝授权时为 {@code null}）
 * @param state 发起授权时生成的防伪随机串
 * @author BlueWhale
 */
public record SocialCallbackCommand(String providerCode, String code, String state) {
}
