package com.bluewhale.auth.application.model.query;

/**
 * 用户会话分页查询（权限中心扩展 M3）
 *
 * @param current 当前页码（从 1 开始）
 * @param size 每页条数
 * @param userId 用户 ID（可空表示不限）
 * @param onlyActive 仅未撤销会话（true 过滤 revoked=0 且未过期）
 * @author BlueWhale
 */
public record SessionPageQuery(long current, long size, Long userId, boolean onlyActive) {
}
