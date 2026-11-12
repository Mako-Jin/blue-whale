package com.bluewhale.auth.domain.repository;

import com.bluewhale.auth.domain.aggregate.RoleAggregate;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Role 聚合仓储端口（ADR-AUTH-004）：契约定义于领域层，由 {@code infrastructure} 适配实现。
 *
 * <p>覆盖角色聚合读写、角色-权限关联维护，以及登录/鉴权链路所需的按用户反查角色与权限码能力
 * （AUTH-17 收口）。</p>
 *
 * @author BlueWhale
 */
public interface RoleRepository {

    /**
     * 保存角色聚合（无 ID 时新增，有 ID 时按主键更新）
     *
     * @param role 角色聚合根
     * @return 回填主键后的角色聚合根
     */
    RoleAggregate save(RoleAggregate role);

    /**
     * 按角色 ID 查询角色聚合（当前租户内查询，跨租户视为不存在）
     *
     * @param id 角色 ID
     * @return 角色聚合；不存在时返回空
     */
    Optional<RoleAggregate> findById(Long id);

    /**
     * 按角色编码查询角色聚合（当前租户内查询）
     *
     * @param roleCode 角色编码
     * @return 角色聚合；不存在时返回空
     */
    Optional<RoleAggregate> findByCode(String roleCode);

    /**
     * 按租户与角色编码查询角色聚合（租户开通等跨租户编排使用）
     *
     * @param tenantId 租户 ID
     * @param roleCode 角色编码
     * @return 角色聚合；不存在时返回空
     */
    Optional<RoleAggregate> findByTenantIdAndCode(Long tenantId, String roleCode);

    /**
     * 按角色 ID 集合查询其中处于启用状态的角色（对应 {@code listRoleCodesByUserId} 对 {@code sys_role} 的读）
     *
     * <p><b>租户口径</b>：本方法不按租户过滤（平台管理员上下文与目标租户不一致的场景
     * 无法依赖行级插件），调用方拿到结果后自行按 {@code role.tenantId()} 过滤或校验。</p>
     *
     * @param ids 角色 ID 集合
     * @return 命中的启用角色聚合列表；{@code ids} 为空时返回空列表
     */
    List<RoleAggregate> listEnabledByIds(Collection<Long> ids);

    /**
     * 分页查询角色（{@code keyword} 模糊匹配角色编码/名称，按 sortNo 升序、createTime 降序）
     *
     * @param current 当前页码
     * @param size 每页条数
     * @param keyword 角色编码或名称模糊匹配关键字（可空）
     * @return 角色分页结果
     */
    RolePage page(long current, long size, String keyword);

    /**
     * 判断角色编码在当前租户内是否已存在（供角色编码唯一性不变量校验）
     *
     * <p><b>并发说明</b>：本方法为「先查后插」的友好预检，存在竞态窗口；真正的唯一性由
     * DB 唯一约束 {@code uk_tenant_role_code(tenant_id, role_code)} 兜底，并发下后插入方将收到
     * 数据库冲突而非重复数据。</p>
     *
     * @param roleCode 角色编码
     * @return 已存在返回 true，否则返回 false
     */
    boolean existsByCode(String roleCode);

    /**
     * 统计当前租户内给定角色 ID 中实际存在的记录数（供分配角色时校验目标角色是否全部有效）
     *
     * @param ids 角色 ID 集合
     * @return 存在的记录数；{@code ids} 为空时返回 0
     */
    long countByIds(Collection<Long> ids);

    /**
     * 删除角色（逻辑删除角色并按物理删除清理角色-权限关联）
     *
     * @param id 角色 ID
     */
    void deleteById(Long id);

    /**
     * 查询用户关联的角色 ID 列表（对应 {@code sys_user_role} 的读）
     *
     * @param userId 用户 ID
     * @return 角色 ID 列表；用户未关联任何角色时返回空列表
     */
    List<Long> listRoleIdsByUserId(Long userId);

    /**
     * 查询给定角色集合关联的权限点 ID（去重）
     *
     * @param roleIds 角色 ID 集合
     * @return 权限点 ID 列表（去重）；{@code roleIds} 为空时返回空列表
     */
    List<Long> listPermissionIdsByRoleIds(Collection<Long> roleIds);

    /**
     * 查询给定权限点中处于启用状态的权限码（去重）
     *
     * @param permissionIds 权限点 ID 集合
     * @return 启用权限码列表（去重）；{@code permissionIds} 为空时返回空列表
     */
    List<String> listEnabledPermissionCodesByIds(Collection<Long> permissionIds);

    /**
     * 查询用户拥有的启用角色编码（用户 → 角色，过滤停用角色以及非当前租户角色）
     *
     * <p>供登录装配与 JWT 过滤器使用，避免安全层依赖应用服务：先查用户角色 ID，为空直接返回空列表，
     * 再按启用状态过滤并映射为角色编码。</p>
     *
     * @param userId 用户 ID
     * @return 启用角色编码列表；用户无角色时返回空列表
     */
    List<String> listRoleCodesByUserId(Long userId);

    /**
     * 查询用户经由角色获得的启用权限码（用户 → 角色 → 权限点，逐层去重并过滤停用）
     *
     * <p>供权限拦截器兜底鉴权使用，避免安全层依赖应用服务。</p>
     *
     * @param userId 用户 ID
     * @return 权限码列表；用户无角色或无有效权限关联时返回空列表
     */
    List<String> listPermissionCodesByUserId(Long userId);

    /**
     * 全量覆盖角色-权限关联（先清空后重建）。
     *
     * <p><b>边界说明</b>：本方法为 {@code sys_role_permission} 关联表端口，而非 Role 聚合内部实体
     * 的持久化通道——Role 聚合根未持有权限关联集合；调用方应先通过聚合根的领域方法
     * {@code assignPermissions} 完成去重与数量校验，再调用本端口落库。须在事务内调用
     * （实现侧 MANDATORY 传播强制）。</p>
     *
     * @param roleId 角色 ID
     * @param permissionIds 目标权限点 ID 列表
     */
    void replacePermissions(Long roleId, List<Long> permissionIds);

    /**
     * 查询角色已分配的权限点 ID 列表
     *
     * @param roleId 角色 ID
     * @return 权限点 ID 列表
     */
    List<Long> listPermissionIds(Long roleId);

    /**
     * 反查持有指定权限点的全部（租户, 用户）对，供权限点变更时跨租户失效用户权限缓存。
     *
     * <p>权限点为平台共享数据，一次变更可能影响多个租户的角色与用户；本方法单条 JOIN SQL
     * 完成「权限 → 角色 → 用户」反查并带出角色所属租户，忽略租户上下文（不按租户过滤）。
     * 仅限缓存失效等平台侧链路调用。</p>
     *
     * @param permissionId 权限点 ID
     * @return （租户 ID, 用户 ID）对集合（去重）；无关联用户时返回空列表
     */
    List<UserTenantRef> listUsersByPermissionId(Long permissionId);

    /**
     * 权限点受影响用户投影：用户 ID 及其角色所属租户 ID
     *
     * @param tenantId 角色所属租户 ID
     * @param userId 用户 ID
     * @author BlueWhale
     */
    record UserTenantRef(long tenantId, long userId) {
    }

    /**
     * 角色分页结果：领域层自有分页载体，避免向领域层泄漏 MyBatis 分页类型。
     *
     * @param records 当前页角色聚合列表
     * @param total 总记录数
     * @author BlueWhale
     */
    record RolePage(List<RoleAggregate> records, long total) {
    }
}
