package com.bluewhale.auth.domain.repository;

import com.bluewhale.auth.domain.aggregate.UserAggregate;

import java.util.Collection;
import java.util.List;
import java.util.Map;
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
    UserAggregate save(UserAggregate user);

    /**
     * 按用户 ID 查询用户聚合（租户内查询，跨租户视为不存在）
     *
     * @param id 用户 ID
     * @return 用户聚合；不存在时返回空
     */
    Optional<UserAggregate> findById(Long id);

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
    Optional<UserAggregate> findByIdAcrossTenants(Long id);

    /**
     * 按 ID 集合批量加载用户聚合，忽略租户行级过滤（跨租户语义同 {@link #findByIdAcrossTenants}）
     *
     * @param ids 用户 ID 集合（空集合返回空列表）
     * @return 实际存在的用户聚合列表（顺序不保证）
     */
    List<UserAggregate> findByIdsAcrossTenants(Collection<Long> ids);

    /**
     * 批量查询多个用户在当前租户内的角色 ID 列表（单次查询，避免逐用户 N+1）
     *
     * @param userIds 用户 ID 集合（空集合返回空映射）
     * @return userId → 当前租户内角色 ID 列表（无角色的用户不出现在结果中）
     */
    Map<Long, List<Long>> listRoleIdsByUserIds(Collection<Long> userIds);

    /**
     * 按用户名查询用户聚合（当前租户内查询）
     *
     * @param username 用户名
     * @return 用户聚合；不存在时返回空
     */
    Optional<UserAggregate> findByUsername(String username);

    /**
     * 按租户与用户名查询用户聚合（Wave 5.54 Phase 0：用户名在租户内唯一）
     *
     * @param tenantId 租户 ID
     * @param username 用户名
     * @return 用户聚合；不存在时返回空
     */
    Optional<UserAggregate> findByTenantIdAndUsername(Long tenantId, String username);

    /**
     * 按租户与邮箱查询启用用户（三方登录「同邮箱」匹配，权限中心扩展 M2）
     *
     * @param tenantId 租户 ID
     * @param email 邮箱（精确匹配，大小写不敏感）
     * @return 启用用户聚合；不存在或已停用/删除时返回空
     */
    Optional<UserAggregate> findEnabledByEmail(Long tenantId, String email);

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
     * 批量查询用户名集合在指定租户内已存在的子集（用户导入预检/提交的批内去重，M4）
     *
     * <p>单次 {@code in} 查询替代逐行 {@link #existsByUsername}，避免导入批次 N+1；
     * 竞态语义与 {@link #existsByUsername} 一致：真正的唯一性由 DB 唯一约束
     * {@code uk_tenant_username(tenant_id, username)} 兜底。</p>
     *
     * @param tenantId 租户 ID
     * @param usernames 用户名集合（空集合返回空列表）
     * @return 已存在的用户名列表
     */
    List<String> listExistingUsernames(Long tenantId, Collection<String> usernames);

    /**
     * 全量覆盖用户在当前租户内的角色关联（只清理并重建本租户角色的关联行）。
     *
     * <p><b>边界说明</b>：本方法为 {@code sys_user_role} 关联表端口，而非 User 聚合内部实体的
     * 持久化通道——User 聚合根未持有角色关联集合；调用方应先通过聚合根的领域方法
     * {@code assignRoles} 完成去重与数量校验，再调用本端口落库。须在事务内调用
     * （实现侧 MANDATORY 传播强制）。</p>
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
     * 反查关联指定角色的全部用户 ID（跨租户全局查询，供权限缓存失效定位受影响用户）。
     *
     * <p>{@code sys_user_role} 无租户维度（角色归属租户，关联行全局唯一），本方法不按租户过滤、
     * 不依赖租户上下文；仅限缓存失效等平台侧链路调用，业务查询禁止依赖该全局语义。</p>
     *
     * @param roleId 角色 ID
     * @return 用户 ID 列表（去重）；无关联用户时返回空列表
     */
    List<Long> listUserIdsByRoleId(Long roleId);

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
    List<UserAggregate> findByRoleCode(String roleCode, int limit);

    /**
     * 批量查询「启用」状态的用户 ID（供服务间成员合法性校验使用）
     *
     * <p><b>租户上下文语义</b>：上下文存在时按上下文租户过滤；上下文为空时退化为跨租户
     * 全局查询——仅限内部令牌（InternalTokenFilter）保护的服务间成员校验场景调用，
     * 公开接口禁止依赖该退化行为。Phase 3 计划改为显式 {@code tenantId} 参数后移除该分支。</p>
     *
     * @param ids 待校验用户 ID 集合（调用方保证去重）
     * @return 存在且启用的用户 ID 列表；入参为空时返回空列表
     */
    List<Long> findEnabledIds(Collection<Long> ids);

    /**
     * 删除用户（逻辑删除用户并按物理删除清理用户-角色关联）
     *
     * <p><b>删除语义</b>：账号级删除——sys_user 行归属首页租户，仅首页租户可触发
     * （行级过滤保证 {@code tenant_id=当前租户}）；账号删除后跨租户成员关系随之失效，
     * 因此角色关联跨租户一并物理清理。</p>
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
    record UserPage(List<UserAggregate> records, long total) {
    }
}
