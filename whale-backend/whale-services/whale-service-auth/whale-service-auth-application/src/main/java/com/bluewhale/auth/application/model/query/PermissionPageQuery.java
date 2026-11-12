package com.bluewhale.auth.application.model.query;

/**
 * 权限点分页查询条件（AUTH-17 / T-DDD-9）
 *
 * @param current 当前页码
 * @param size 每页条数
 * @param keyword 权限编码或名称模糊匹配关键字（可空）
 * @param permType 权限类型精确过滤条件（可空）
 * @author BlueWhale
 */
public record PermissionPageQuery(long current, long size, String keyword, String permType) {
}
