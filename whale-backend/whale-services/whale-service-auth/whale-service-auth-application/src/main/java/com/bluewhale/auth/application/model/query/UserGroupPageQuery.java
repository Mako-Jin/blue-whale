package com.bluewhale.auth.application.model.query;

/**
 * 用户组分页查询（权限中心扩展 M3）
 *
 * @param current 当前页码（从 1 开始）
 * @param size 每页条数
 * @param keyword 关键字（模糊匹配编码/名称，可空）
 * @param category 类别过滤（STATIC / DYNAMIC，可空）
 * @author BlueWhale
 */
public record UserGroupPageQuery(long current, long size, String keyword, String category) {
}
