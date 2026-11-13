package com.bluewhale.auth.application.model.vo;

import com.bluewhale.auth.domain.aggregate.RoleConstraintAggregate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 角色约束视图对象（权限中心扩展 M3）
 *
 * @param id 主键
 * @param tenantId 租户 ID
 * @param constraintCode 约束编码
 * @param constraintName 约束名称
 * @param constraintType 约束类型（MUTEX / CARDINALITY）
 * @param roleIds 覆盖角色 ID 集合
 * @param maxAssign 基数上限（仅 CARDINALITY）
 * @param status 状态（1 启用 / 0 停用）
 * @param description 描述
 * @param createTime 创建时间
 * @param updateTime 更新时间
 * @author BlueWhale
 */
public record RoleConstraintVO(Long id, Long tenantId, String constraintCode, String constraintName,
                               String constraintType, List<Long> roleIds, Integer maxAssign, int status,
                               String description, LocalDateTime createTime, LocalDateTime updateTime) {

    /**
     * 由聚合装配视图
     *
     * @param constraint 约束聚合
     * @return 视图对象
     */
    public static RoleConstraintVO from(RoleConstraintAggregate constraint) {
        return new RoleConstraintVO(constraint.getId(), constraint.getTenantId(),
                constraint.getConstraintCode(), constraint.getConstraintName(),
                constraint.getConstraintType().name(), constraint.getRoleIds(), constraint.getMaxAssign(),
                constraint.getStatus(), constraint.getDescription(),
                constraint.getCreateTime(), constraint.getUpdateTime());
    }
}
