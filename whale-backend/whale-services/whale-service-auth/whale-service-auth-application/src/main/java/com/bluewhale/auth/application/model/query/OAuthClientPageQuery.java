package com.bluewhale.auth.application.model.query;

/**
 * OAuth2 客户端分页查询（权限中心扩展 M0）。
 *
 * @param current 当前页码
 * @param size 每页条数
 * @param keyword 客户端标识/名称模糊匹配关键字（可空）
 * @param status 状态精确过滤（可空）
 * @author BlueWhale
 */
public record OAuthClientPageQuery(long current, long size, String keyword, Integer status) {
}
