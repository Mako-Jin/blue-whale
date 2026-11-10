package com.bluewhale.auth.application.tenant.query;

/**
 * 租户分页查询：承载平台租户「租户列表」用例的查询条件。
 *
 * @param current 当前页码
 * @param size 每页条数
 * @param keyword 租户编码或名称模糊匹配关键字（可空）
 * @param status 状态精确过滤（可空）
 * @author BlueWhale
 */
public record TenantPageQuery(long current, long size, String keyword, Integer status) {
}
