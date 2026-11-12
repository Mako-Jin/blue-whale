package com.bluewhale.auth.application.model.result;

/**
 * 二次认证结果
 *
 * @param token step-up 凭证（请求头 {@code X-Reauth-Token}）
 * @param expiresInSeconds 有效期秒数
 * @author BlueWhale
 */
public record ReauthResult(String token, long expiresInSeconds) {
}
