package com.bluewhale.auth.domain.model.entity;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;
import com.bluewhale.common.domain.model.Entity;

/**
 * 角色-权限分配实体（对应 {@code sys_role_permission}，REQ-ARCH-001 / T-DDD-5）：作为 Role 聚合的内部实体，
 * 仅在聚合内维护，删除随聚合根级联（关联表实体无 {@code deleted} 字段，走物理删除）。
 *
 * <p><b>判等语义</b>：本类为 {@code record}，按全字段判等（含 {@code id}）——
 * 同一 {@code (roleId, permissionId)} 但 {@code id} 不同（如一条新建未落库、一条已落库）的实例不相等；
 * 需要按业务键判等时请比较 {@code roleId + permissionId}。</p>
 *
 * @param id 关联记录 ID（新建时为 {@code null}）
 * @param roleId 角色 ID
 * @param permissionId 权限点 ID
 * @author BlueWhale
 */
public record RolePermissionAssignment(Long id, Long roleId, Long permissionId) implements Entity<Long> {

    /**
     * 紧凑构造器：校验角色 ID 与权限点 ID 非空
     *
     * @param id 关联记录 ID（新建时为 {@code null}）
     * @param roleId 角色 ID
     * @param permissionId 权限点 ID
     */
    public RolePermissionAssignment {
        if (roleId == null || permissionId == null) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "角色-权限分配的角色与权限 ID 不能为空");
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

    /**
     * 获取关联记录主键
     *
     * @return 关联记录 ID；新建且未落库时为 {@code null}
     */
    public Long getId() {
        return id;
    }
}
