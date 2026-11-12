package com.bluewhale.auth.domain.model.entity;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;
import com.bluewhale.common.domain.model.Entity;

/**
 * 用户-角色分配实体（对应 {@code sys_user_role}，REQ-ARCH-001 / T-DDD-4）：作为 User 聚合的内部实体，
 * 仅在聚合内维护，删除随聚合根级联（关联表实体无 {@code deleted} 字段，走物理删除）。
 *
 * <p><b>判等语义</b>：本类为 {@code record}，按全字段判等（含 {@code id}）——
 * 同一 {@code (userId, roleId)} 但 {@code id} 不同（如一条新建未落库、一条已落库）的实例不相等；
 * 需要按业务键判等时请比较 {@code userId + roleId}。</p>
 *
 * @param id 关联记录 ID（新建时为 {@code null}）
 * @param userId 用户 ID
 * @param roleId 角色 ID
 * @author BlueWhale
 */
public record UserRoleAssignment(Long id, Long userId, Long roleId) implements Entity<Long> {

    /**
     * 紧凑构造器：校验用户 ID 与角色 ID 非空
     *
     * @param id 关联记录 ID（新建时为 {@code null}）
     * @param userId 用户 ID
     * @param roleId 角色 ID
     */
    public UserRoleAssignment {
        if (userId == null || roleId == null) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "用户-角色分配的用户与角色 ID 不能为空");
        }
    }

    /**
     * 由用户与角色 ID 创建分配实体（无关联记录 ID）
     *
     * @param userId 用户 ID
     * @param roleId 角色 ID
     * @return 用户-角色分配实体
     */
    public static UserRoleAssignment of(Long userId, Long roleId) {
        return new UserRoleAssignment(null, userId, roleId);
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
