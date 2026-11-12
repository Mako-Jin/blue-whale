package com.bluewhale.auth.domain.model.entity;

/**
 * 聚合内部实体包：仅在所属聚合内维护、不经仓储直接操作的关联实体（REQ-ARCH-001）。
 *
 * <p>包含：{@code UserRoleAssignment}（用户-角色分配，User 聚合内部实体）、
 * {@code RolePermissionAssignment}（角色-权限分配，Role 聚合内部实体）。
 * 生命周期与不变量由聚合根方法（如 {@code assignRoles} / {@code assignPermissions}）统一维护。</p>
 *
 * <p><b>硬约束</b>：本包内实体只允许经所属聚合根的方法维护（新增/变更/移除全部由聚合根
 * 内部完成），仓储与仓储实现禁止对本包实体直接增删改；仓储加载聚合时负责装配内部实体，
 * 持久化时由聚合根对应的仓储适配器整体写回。绕过聚合根直接操作内部实体视为架构违规，
 * 由 architecture-test 与评审双重把关。</p>
 *
 * @author BlueWhale
 */
