package com.bluewhale.auth.application.auth.result;

import java.util.List;

/**
 * 登录 / 刷新 / 切换租户结果（V-03：refreshToken 仅由接口层写入 HttpOnly Cookie，不进入响应体）。
 *
 * @param accessToken 新签发的 accessToken
 * @param tokenType 固定 Bearer
 * @param expiresIn accessToken 有效期（秒）
 * @param userId 用户 ID
 * @param tenantId 令牌所属租户 ID
 * @param username 用户名
 * @param nickname 昵称
 * @param roles 角色 code 列表
 * @param mustChangePassword 是否强制改密
 * @param passwordExpired 口令是否已到期
 * @param passwordExpiresInDays 距口令到期剩余天数；不适用为 -1
 * @param refreshToken 新签发的 refreshToken（仅接口层写 Cookie）
 * @author BlueWhale
 */
public record LoginResult(String accessToken,
                          String tokenType,
                          long expiresIn,
                          Long userId,
                          Long tenantId,
                          String username,
                          String nickname,
                          List<String> roles,
                          boolean mustChangePassword,
                          boolean passwordExpired,
                          long passwordExpiresInDays,
                          String refreshToken) {
}
