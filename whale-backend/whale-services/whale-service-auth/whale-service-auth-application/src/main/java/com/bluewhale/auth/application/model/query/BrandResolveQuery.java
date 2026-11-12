package com.bluewhale.auth.application.model.query;

/**
 * 登录页品牌解析查询（权限中心扩展 M0，免登录）。
 *
 * @param targetType 作用域类型（可空，默认 PLATFORM）
 * @param targetId 作用域 ID（可空，默认 0）
 * @param domain 请求域名（可空）
 * @author BlueWhale
 */
public record BrandResolveQuery(String targetType, Long targetId, String domain) {
}
