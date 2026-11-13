package com.bluewhale.auth.application.model.command;

import java.util.List;

/**
 * 保存权限策略命令（权限中心扩展 M3）。
 *
 * <p>{@code id} 为空表示新建；{@code conditionJson} 为附加条件 JSON 数组（可空表示无条件）。</p>
 *
 * @param id 主键（新建时为 {@code null}）
 * @param strategyCode 策略编码（新建必填，租户内唯一，更新不可变）
 * @param strategyName 策略名称
 * @param strategyType 策略类型（GRANT / DENY，新建必填）
 * @param roleIds 生效角色 ID 集合（必填非空）
 * @param resourceIds 生效资源 ID 集合（必填非空）
 * @param conditionJson 附加条件 JSON（可空）
 * @param isDefault 是否默认策略（同租户仅一条，由服务编排）
 * @param priority 优先级（可空默认 100，数字小者优先）
 * @param status 状态（可空，新建默认启用）
 * @param description 描述（可空）
 * @author BlueWhale
 */
public record SavePermissionStrategyCommand(Long id, String strategyCode, String strategyName,
                                            String strategyType, List<Long> roleIds, List<Long> resourceIds,
                                            String conditionJson, Boolean isDefault, Integer priority,
                                            Integer status, String description) {
}
