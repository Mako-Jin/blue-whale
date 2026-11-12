package com.bluewhale.auth.application.model.command;

import java.util.List;

/**
 * OAuth2 客户端保存命令（权限中心扩展 M0）。
 *
 * @param clientId 客户端标识（新建时可空，由应用层生成）
 * @param clientName 客户端名称
 * @param redirectUris 回调地址白名单（authorization_code 必填）
 * @param grantTypes 授权类型（authorization_code / client_credentials / refresh_token）
 * @param scopes 授权范围（映射 sys_permission.perm_code 前缀）
 * @param accessTokenTtl 访问令牌有效期（秒）
 * @param refreshTokenTtl 刷新令牌有效期（秒）
 * @param autoApprove 是否免确认授权
 * @param status 状态（1 启用 / 0 停用）；为空时保持原状态
 * @author BlueWhale
 */
public record SaveOAuthClientCommand(String clientId, String clientName, List<String> redirectUris,
                                     List<String> grantTypes, List<String> scopes, Integer accessTokenTtl,
                                     Integer refreshTokenTtl, Boolean autoApprove, Integer status) {
}
