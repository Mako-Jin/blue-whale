package com.bluewhale.auth.application.model.result;

/**
 * 三方授权起始结果（权限中心扩展 M2）。
 *
 * @param authorizeUrl 渠道授权页完整 URL（前端直接跳转）
 * @param state 本次授权的防伪随机串（仅日志/联调用，前端无需持有）
 * @author BlueWhale
 */
public record SocialAuthorizeResult(String authorizeUrl, String state) {
}
