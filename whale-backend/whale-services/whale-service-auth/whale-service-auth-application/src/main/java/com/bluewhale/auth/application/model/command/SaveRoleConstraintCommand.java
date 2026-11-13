package com.bluewhale.auth.application.model.command;

import java.util.List;

/**
 * 保存角色约束命令（权限中心扩展 M3）。
 *
 * <p>{@code id} 为空表示新建；MUTEX 约束需 ≥2 角色；CARDINALITY 约束 {@code maxAssign} &gt; 0。</p>
 *
 * @param id 主键（新建时为 {@code null}）
 * @param constraintCode 约束编码（新建必填，租户内唯一，更新不可变）
 * @param constraintName 约束名称
 * @param constraintType 约束类型（MUTEX / CARDINALITY，新建必填）
 * @param roleIds 约束覆盖的角色 ID 集合（必填非空）
 * @param maxAssign 基数上限（仅 CARDINALITY，可空）
 * @param status 状态（可空，新建默认启用）
 * @param description 描述（可空）
 * @author BlueWhale
 */
public record SaveRoleConstraintCommand(Long id, String constraintCode, String constraintName,
                                        String constraintType, List<Long> roleIds, Integer maxAssign,
                                        Integer status, String description) {
}
