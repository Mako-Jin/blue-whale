package com.bluewhale.auth.application.model.social;

/**
 * 一次性登录码载荷（权限中心扩展 M2）：三方登录成功后签发给前端换取令牌的凭证。
 *
 * @param userId 登录用户 ID
 * @param tenantId 登录租户 ID
 * @param createdAtEpochMs 签发时间（epoch 毫秒）
 * @author BlueWhale
 */
public record SocialLoginCodeRecord(Long userId, Long tenantId, long createdAtEpochMs) {
}
