package com.bluewhale.auth.application.model.command;

/**
 * 网关访问名单（白名单 / 黑名单）保存命令。
 *
 * <p>两类名单字段结构一致，统一使用该命令；{@code null} 字段由聚合按默认值/原值处理。</p>
 *
 * @param pathPattern 路径（Ant 通配）
 * @param httpMethod HTTP 方法，空值表示不限
 * @param status 状态（1 启用/生效、0 停用/解除），空值新增时默认启用
 * @param sortNo 排序号，空值按 0 处理
 * @param remark 备注
 * @author BlueWhale
 */
public record SaveGatewayAccessRuleCommand(String pathPattern, String httpMethod, Integer status, Integer sortNo,
                                           String remark) {
}
