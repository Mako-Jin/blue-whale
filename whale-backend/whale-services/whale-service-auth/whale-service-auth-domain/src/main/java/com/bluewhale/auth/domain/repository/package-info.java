package com.bluewhale.auth.domain.repository;

/**
 * 仓储端口包：全部聚合的仓储接口按「领域层按功能分包」口径统一收纳于此。
 *
 * <p>接口定义于领域层，由 {@code infrastructure} 层适配实现（依赖倒置，ADR-AUTH-004）；
 * 领域层禁止出现 MyBatis-Plus / Spring 等具体实现类型。</p>
 *
 * <p>包含：{@code UserRepository}、{@code UserTenantRepository}、{@code PasswordHistoryRepository}、
 * {@code RoleRepository}、{@code DeptRepository}、{@code MenuRepository}、
 * {@code PermissionRepository}、{@code UserPermissionRepository}、{@code TenantRepository}。</p>
 *
 * @author BlueWhale
 */
