package com.bluewhale.auth.domain.role.entity;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;

/**
 * 角色-权限分配实体（对应 {@code sys_role_permission}，REQ-ARCH-001 / T-DDD-5）：作为 Role 聚合的内部实体，
 * 仅在聚合内维护，删除随聚合根级联（关联表实体无 {@code deleted} 字段，走物理删除）。
 *
 * @param id 关联记录 ID（新建时为 {@code null}）
 * @param roleId 角色 ID
 * @param permissionId 权限点 ID
 * @author BlueWhale
 */
public record RolePermissionAssignment(Long id, Long roleId, Long permissionId) {

    /**
     * 紧凑构造器：校验角色 ID 与权限点 ID 非空
     *
     * @param id 关联记录 ID（新建时为 {@code null}）
     * @param roleId 角色 ID
     * @param permissionId 权限点 ID
     */
    public RolePermissionAssignment {
        if (roleId == null || permissionId == null) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "角色-权限分配的角色与权限 ID 不能为空");
        }
    }

    /**
     * 由角色与权限点 ID 创建分配实体（无关联记录 ID）
     *
     * @param roleId 角色 ID
     * @param permissionId 权限点 ID
     * @return 角色-权限分配实体
     */
    public static RolePermissionAssignment of(Long roleId, Long permissionId) {
        return new RolePermissionAssignment(null, roleId, permissionId);
    }
}
