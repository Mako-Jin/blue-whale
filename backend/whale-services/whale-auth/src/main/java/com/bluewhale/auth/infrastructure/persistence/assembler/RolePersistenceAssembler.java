package com.bluewhale.auth.infrastructure.persistence.assembler;

import com.bluewhale.auth.domain.model.vo.RoleCode;
import com.bluewhale.auth.domain.role.aggregate.Role;
import com.bluewhale.auth.domain.role.entity.RolePermissionAssignment;
import com.bluewhale.auth.entity.SysRole;
import com.bluewhale.auth.entity.SysRolePermission;

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
    public static Role toDomain(SysRole po) {
        return Role.reconstitute(
                po.getId(),
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
    public static SysRole toPo(Role role) {
        SysRole po = new SysRole();
        po.setId(role.getId());
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
    public static RolePermissionAssignment toAssignment(SysRolePermission po) {
        return new RolePermissionAssignment(po.getId(), po.getRoleId(), po.getPermissionId());
    }

    /**
     * 领域对象 → PO：角色-权限分配
     *
     * @param assignment 角色-权限分配实体
     * @return 角色-权限关联持久化对象
     */
    public static SysRolePermission toAssignmentPo(RolePermissionAssignment assignment) {
        SysRolePermission po = new SysRolePermission();
        po.setId(assignment.id());
        po.setRoleId(assignment.roleId());
        po.setPermissionId(assignment.permissionId());
        return po;
    }
}
