package com.bluewhale.auth.application.model.query;

/**
 * 角色分页查询：承载管理端「分页查询角色」用例的查询条件。
 *
 * @param current 当前页码
 * @param size 每页条数
 * @param keyword 角色编码或名称模糊匹配关键字（可空）
 * @author BlueWhale
 */
public record RolePageQuery(long current, long size, String keyword) {
}
