package com.bluewhale.auth.application.user.query;

/**
 * 用户分页查询：承载管理端「分页查询用户」用例的查询条件。
 *
 * @param current 当前页码
 * @param size 每页条数
 * @param keyword 用户名/昵称/手机号模糊匹配关键字（可空）
 * @author BlueWhale
 */
public record UserPageQuery(long current, long size, String keyword) {
}
