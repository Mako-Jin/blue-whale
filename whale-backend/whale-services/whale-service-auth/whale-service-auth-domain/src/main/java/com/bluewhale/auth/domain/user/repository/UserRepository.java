package com.bluewhale.auth.domain.user.repository;

import com.bluewhale.auth.domain.user.aggregate.User;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * User 聚合仓储端口（ADR-AUTH-004）：契约定义于领域层，由 {@code infrastructure} 适配实现。
 *
 * @author BlueWhale
 */
public interface UserRepository {

    /**
     * 保存用户聚合（无 ID 时新增，有 ID 时按主键更新）
     *
     * @param user 用户聚合根
     * @return 回填主键后的用户聚合根
     */
    User save(User user);

    /**
     * 按用户 ID 查询用户聚合（租户内查询，跨租户视为不存在）
     *
     * @param id 用户 ID
     * @return 用户聚合；不存在时返回空
     */
    Optional<User> findById(Long id);

    /**
     * 按用户 ID 加载用户聚合，忽略租户行级过滤（Wave 5.54 Phase 5.1）。
     *
     * <p>{@code sys_user} 归属首页租户，而令牌租户可能是该用户切换后的其他租户，
     * 因此刷新、JWT 过滤器与切换必须按全局唯一 {@code userId} 定位账号，不能按
     * {@code (tenantId, userId)} 查。调用方仍需自行校验用户与令牌租户的成员关系。</p>
     *
     * @param id 用户 ID
     * @return 用户聚合；不存在或已逻辑删除时返回空
     */
    Optional<User> findByIdAcrossTenants(Long id);

    /**
     * 按用户名查询用户聚合（当前租户内查询）
     *
     * @param username 用户名
     * @return 用户聚合；不存在时返回空
     */
    Optional<User> findByUsername(String username);

    /**
     * 按租户与用户名查询用户聚合（Wave 5.54 Phase 0：用户名在租户内唯一）
     *
     * @param tenantId 租户 ID
     * @param username 用户名
     * @return 用户聚合；不存在时返回空
     */
    Optional<User> findByTenantIdAndUsername(Long tenantId, String username);

    /**
     * 判断用户名在指定租户内是否已存在（供用户唯一性不变量跨聚合校验）
     *
     * <p><b>并发说明</b>：本方法为「先查后插」的友好预检，存在竞态窗口；真正的唯一性由
     * DB 唯一约束 {@code uk_tenant_username(tenant_id, username)} 兜底，并发下后插入方将收到
     * 数据库冲突而非重复数据。</p>
     *
     * @param tenantId 租户 ID
     * @param username 用户名
     * @return 已存在返回 true，否则返回 false
     */
    boolean existsByUsername(Long tenantId, String username);

    /**
     * 全量覆盖用户在当前租户内的角色关联（只清理并重建本租户角色的关联行）。
     *
     * <p><b>边界说明</b>：本方法为 {@code sys_user_role} 关联表端口，而非 User 聚合内部实体的
     * 持久化通道——User 聚合根未持有角色关联集合；调用方应先通过聚合根的领域方法
     * {@code assignRoles} 完成去重与数量校验，再调用本端口落库。</p>
     *
     * @param userId 用户 ID
     * @param roleIds 目标角色 ID 列表（属于当前租户）
     */
    void replaceRoles(Long userId, List<Long> roleIds);

    /**
     * 查询用户在当前租户内的角色 ID 列表
     *
     * @param userId 用户 ID
     * @return 角色 ID 列表
     */
    List<Long> listRoleIds(Long userId);

    /**
     * 物理删除用户与指定租户角色的关联行（成员解绑时清理租户内角色）。
     *
     * <p>只删除 {@code sys_role.tenant_id=指定租户} 的角色关联，不影响该用户在其他租户的
     * 角色；调用方需自行保证租户 ID 可信。</p>
     *
     * @param userId 用户 ID
     * @param tenantId 目标租户 ID
     */
    void removeRolesInTenant(Long userId, Long tenantId);

    /**
     * 分页查询用户（{@code keyword} 模糊匹配用户名/昵称/手机号，按创建时间降序）
     *
     * @param current 当前页码
     * @param size 每页条数
     * @param keyword 用户名/昵称/手机号模糊匹配关键字（可空）
     * @return 用户分页结果
     */
    UserPage page(long current, long size, String keyword);

    /**
     * 分页查询通讯录用户（仅启用且未删除用户；{@code deptIds} 精确匹配其中之一，
     * {@code keyword} 模糊匹配用户名/昵称/手机号，按用户 ID 升序稳定排序）
     *
     * @param current 当前页码
     * @param size 每页条数
     * @param deptIds 部门 ID 集合（{@code null} 表示不过滤；空集合表示「没有任何匹配部门」，
     *                调用方应直接返回空页，不应下推到 SQL 生成 {@code in ()}）
     * @param keyword 用户名/昵称/手机号模糊匹配关键字（可空）
     * @return 用户分页结果
     */
    UserPage directoryPage(long current, long size, Collection<Long> deptIds, String keyword);

    /**
     * 按角色编码查询当前租户内的启用用户（角色不存在或已停用时返回空列表）
     *
     * @param roleCode 角色编码
     * @param limit 返回条数上限
     * @return 启用用户聚合列表，按用户 ID 升序
     */
    List<User> findByRoleCode(String roleCode, int limit);

    /**
     * 批量查询「启用」状态的用户 ID（供服务间成员合法性校验使用）
     *
     * @param ids 待校验用户 ID 集合（调用方保证去重）
     * @return 存在且启用的用户 ID 列表；入参为空时返回空列表
     */
    List<Long> findEnabledIds(Collection<Long> ids);

    /**
     * 删除用户（逻辑删除用户并按物理删除清理用户-角色关联）
     *
     * @param id 用户 ID
     */
    void deleteById(Long id);

    /**
     * 用户分页结果：领域层自有分页载体，避免向领域层泄漏 MyBatis 分页类型。
     *
     * @param records 当前页用户聚合列表
     * @param total 总记录数
     * @author BlueWhale
     */
    record UserPage(List<User> records, long total) {
    }
}
