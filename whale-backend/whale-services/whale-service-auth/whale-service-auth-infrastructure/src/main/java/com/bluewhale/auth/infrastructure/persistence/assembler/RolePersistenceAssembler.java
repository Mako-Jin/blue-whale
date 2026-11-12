package com.bluewhale.auth.infrastructure.persistence.assembler;

import com.bluewhale.auth.domain.model.vo.RoleCode;
import com.bluewhale.auth.domain.aggregate.RoleAggregate;
import com.bluewhale.auth.domain.model.entity.RolePermissionAssignment;
import com.bluewhale.auth.infrastructure.persistence.entity.SysRolePo;
import com.bluewhale.auth.infrastructure.persistence.entity.SysRolePermissionPo;

/**
 * Role 聚合与其持久化对象（PO）的双向转换器（手写 Assembler，REQ-ARCH-001 / T-DDD-5）。
 *
 * <p>选择手写而非 MapStruct：角色编码以值对象（{@link RoleCode}）承载，且需对 {@code sortNo}/{@code status}
 * 做空值归一化，显式转换更可控、可读。未映射字段 {@code deleted} 不属于 Role 聚合关键项，转换时保持 PO 原值不动。</p>
 *
 * @author BlueWhale
 */
public final class RolePersistenceAssembler {

    /**
     * 私有构造器：工具类，禁止实例化
     */
    private RolePersistenceAssembler() {
    }

    /**
     * PO → 领域对象：{@code sortNo}/{@code status} 为空时按 0 / 启用归一化
     *
     * @param po 角色持久化对象
     * @return 角色聚合根
     */
    public static RoleAggregate toDomain(SysRolePo po) {
        return RoleAggregate.reconstitute(
                po.getId(),
                po.getTenantId(),
                RoleCode.of(po.getRoleCode()),
                po.getRoleName(),
                po.getDescription(),
                po.getSortNo(),
                po.getStatus(),
                po.getCreateTime(),
                po.getUpdateTime());
    }

    /**
     * 领域对象 → PO：仅转换 Role 聚合所承载的字段
     *
     * @param role 角色聚合根
     * @return 角色持久化对象
     */
    public static SysRolePo toPo(RoleAggregate role) {
        SysRolePo po = new SysRolePo();
        po.setId(role.getId());
        po.setTenantId(role.getTenantId());
        po.setRoleCode(role.getRoleCode().value());
        po.setRoleName(role.getRoleName());
        po.setDescription(role.getDescription());
        po.setSortNo(role.getSortNo());
        po.setStatus(role.getStatus());
        po.setCreateTime(role.getCreateTime());
        po.setUpdateTime(role.getUpdateTime());
        return po;
    }

    /**
     * PO → 领域对象：角色-权限分配
     *
     * @param po 角色-权限关联持久化对象
     * @return 角色-权限分配实体
     */
    public static RolePermissionAssignment toAssignment(SysRolePermissionPo po) {
        return new RolePermissionAssignment(po.getId(), po.getRoleId(), po.getPermissionId());
    }

    /**
     * 领域对象 → PO：角色-权限分配
     *
     * @param assignment 角色-权限分配实体
     * @return 角色-权限关联持久化对象
     */
    public static SysRolePermissionPo toAssignmentPo(RolePermissionAssignment assignment) {
        SysRolePermissionPo po = new SysRolePermissionPo();
        po.setId(assignment.id());
        po.setRoleId(assignment.roleId());
        po.setPermissionId(assignment.permissionId());
        return po;
    }
}
