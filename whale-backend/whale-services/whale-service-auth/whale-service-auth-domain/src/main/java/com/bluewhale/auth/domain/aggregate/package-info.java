package com.bluewhale.auth.domain.aggregate;

/**
 * 聚合根包：auth 全部聚合根按「领域层按功能分包」口径统一收纳于此（REQ-ARCH-001）。
 *
 * <p>包含：{@code UserAggregate}（用户）、{@code RoleAggregate}（角色）、{@code DeptAggregate}（部门）、
 * {@code MenuAggregate}（菜单）、{@code PermissionAggregate}（权限点）、{@code TenantAggregate}（租户）、
 * {@code UserPermissionAttributeAggregate}/{@code UserPermissionKeyAggregate}
 * （用户-权限 ABAC 属性聚合）。聚合根类名统一以 {@code Aggregate} 后缀标识所属类别。
 * 租户到期时钟 {@code TenantExpiryClock} 为领域工具，收纳于 {@code domain.model}。</p>
 *
 * <p>各聚合根的关键不变量与行为集中在自身，仓储端口一律声明于 {@code domain.repository}，
 * 由 {@code infrastructure} 适配实现（依赖倒置，ADR-AUTH-004）。</p>
 *
 * @author BlueWhale
 */
