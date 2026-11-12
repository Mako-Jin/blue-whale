package com.bluewhale.auth.domain.model.entity;

/**
 * 聚合内部实体包：仅在所属聚合内维护、不经仓储直接操作的关联实体（REQ-ARCH-001）。
 *
 * <p>包含：{@code UserRoleAssignment}（用户-角色分配，User 聚合内部实体）、
 * {@code RolePermissionAssignment}（角色-权限分配，Role 聚合内部实体）。
 * 生命周期与不变量由聚合根方法（如 {@code assignRoles} / {@code assignPermissions}）统一维护。</p>
 *
 * @author BlueWhale
 */
