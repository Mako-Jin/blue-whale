package com.bluewhale.auth.application.model.query;

/**
 * 品牌配置分页查询（权限中心扩展 M0）。
 *
 * @param current 当前页码
 * @param size 每页条数
 * @param keyword 品牌名称/域名模糊匹配关键字（可空）
 * @param targetType 作用域类型精确过滤（可空）
 * @param status 状态精确过滤（可空）
 * @author BlueWhale
 */
public record BrandPageQuery(long current, long size, String keyword, String targetType, Integer status) {
}
