package com.bluewhale.auth.application.model.command;

/**
 * 网关访问名单（白名单 / 黑名单）保存命令。
 *
 * <p>两类名单字段结构一致，统一使用该命令；{@code null} 字段由聚合按默认值/原值处理。
 * {@code scope}/{@code tenantId} 仅白名单使用（GW-08 租户维度），黑名单保存时忽略。</p>
 *
 * @param pathPattern 路径（Ant 通配）
 * @param httpMethod HTTP 方法，空值表示不限
 * @param scope 作用域（PLATFORM / TENANT），空值默认平台级；仅白名单使用
 * @param tenantId 租户 ID：平台级传 0 或空，租户级必须大于 0；仅白名单使用
 * @param status 状态（1 启用/生效、0 停用/解除），空值新增时默认启用
 * @param sortNo 排序号，空值按 0 处理
 * @param remark 备注
 * @author BlueWhale
 */
public record SaveGatewayAccessRuleCommand(String pathPattern, String httpMethod, String scope, Long tenantId,
                                           Integer status, Integer sortNo, String remark) {
}
