package com.bluewhale.auth.application.model.command;

/**
 * 品牌配置保存命令（权限中心扩展 M0）。
 *
 * @param targetType 作用域类型（PLATFORM / TENANT / COMPANY）
 * @param targetId 作用域 ID（PLATFORM 固定 0）
 * @param domain 绑定域名（可空，空串表示默认品牌）
 * @param brandName 品牌名称
 * @param logoUrl Logo 地址
 * @param loginTitle 登录页标题
 * @param institutionName 机构全称
 * @param copyright 版权信息
 * @param primaryColor 主题色（#RGB / #RRGGBB）
 * @param backgroundUrl 背景图地址
 * @param priority 解析优先级（数字越小越优先）
 * @param status 状态（1 启用 / 0 停用）；新建为空时按启用处理
 * @author BlueWhale
 */
public record SaveBrandConfigCommand(String targetType, Long targetId, String domain, String brandName,
                                     String logoUrl, String loginTitle, String institutionName,
                                     String copyright, String primaryColor, String backgroundUrl,
                                     Integer priority, Integer status) {
}
