package com.bluewhale.auth.infrastructure.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.auth.infrastructure.config.PermissionCacheProperties;
import com.bluewhale.auth.infrastructure.persistence.entity.RoleTenantRef;
import com.bluewhale.auth.infrastructure.persistence.entity.SysRolePermission;
import com.bluewhale.auth.infrastructure.persistence.entity.SysUserRole;
import com.bluewhale.auth.infrastructure.cache.AuthPermissionCacheInvalidator;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysRoleMapper;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysRolePermissionMapper;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysUserRoleMapper;
import com.bluewhale.common.cache.CacheConsistencyTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 权限判定缓存（Cache-Aside，REQ-DIST-003 场景接入）。
 * <p>读路径：{@link #getPermissionCodes(Long, Supplier)} 命中缓存直接返回，未命中回源并写缓存
 * （空集合写空串，读回空串即空集合）；Redis 异常由 {@link CacheConsistencyTemplate} 内部降级直查，
 * 不阻断主流程。</p>
 * <p>写路径：{@link #evictUser(Long)} / {@link #evictUsers(Collection)} / {@link #evictByRole(Long)} /
 * {@link #evictByPermission(Long)} 由 {@code AuthCacheEvictionListener} 在
 * {@code @TransactionalEventListener(AFTER_COMMIT)} 中调用，方法内部<b>不再二次延迟</b>：
 * 监听器触发时事务已经提交，若再次注册事务同步回调，新回调不会被本轮 afterCommit 执行，
 * 会导致缓存实际未失效。若未来需要在事务内直接调用，应改为发布领域事件。</p>
 * <p>删除动作委托 {@link AuthPermissionCacheInvalidator}，同时完成跨实例广播与
 * Redis 断连后的待失效重对齐。</p>
 * <p>本类不注入 {@code RoleAppService}：读路径的 DB 查询由调用方以 {@link Supplier} 传入，
 * 失效路径的受影响用户反查直接走 Mapper，避免 {@code RoleAppService → PermissionCache →
 * RoleAppService} 循环依赖，也不复制权限码查询逻辑。</p>
 * <p>击穿防护（REQ-DIST-003）：未命中时经独立 Bean {@link PermissionCacheMutex} 互斥回源，并在锁内
 * <b>复查缓存</b>，使同 Key 并发仅一次回源；锁/Redis 异常时降级直查，不阻断权限判定。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionCache {

    /** 权限码缓存名，最终 Key 为 {@code whale:cache:auth:user:perm:{userId}}（REQ-ARCH-004） */
    public static final String PERMISSION_CACHE_NAME = "auth:user:perm";

    private final CacheConsistencyTemplate cacheTemplate;
    private final PermissionCacheProperties properties;
    private final PermissionCacheMutex mutex;
    private final AuthPermissionCacheInvalidator cacheInvalidator;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysRoleMapper roleMapper;

    /**
     * 读取用户权限码集合（Cache-Aside）
     *
     * @param userId 用户 ID
     * @param loader 缓存未命中时的回源加载器，返回权限码列表
     * @return 权限码集合；无权限或命中空结果哨兵时返回空集合
     */
    public Set<String> getPermissionCodes(Long userId, Supplier<List<String>> loader) {
        if (!properties.isEnabled()) {
            return toSet(loader.get());
        }
        String value = cacheTemplate.getOrLoad(PERMISSION_CACHE_NAME, String.valueOf(userId),
                properties.getTtl(), () -> loadUnderLock(userId, loader));
        if (value == null || value.isEmpty()) {
            return Set.of();
        }
        return new LinkedHashSet<>(Arrays.asList(value.split(",")));
    }

    /**
     * 互斥回源（防击穿）：经 {@link PermissionCacheMutex} 加锁后在锁内复查缓存，命中则直接返回，
     * 未命中才真正回源；锁或 Redis 异常时降级直查，保证缓存故障不阻断权限判定。
     *
     * @param userId 用户 ID，作为锁 Key 的业务标识
     * @param loader 缓存未命中时的回源加载器，返回权限码列表
     * @return 逗号拼接的权限码串（空集合为空串）
     */
    private String loadUnderLock(Long userId, Supplier<List<String>> loader) {
        try {
            return mutex.load(userId, () -> {
                String cached = cacheTemplate.getIfPresent(PERMISSION_CACHE_NAME, String.valueOf(userId));
                if (cached != null) {
                    return cached;
                }
                // 未命中：在锁内完成回源 + 写缓存，使持锁期结束时缓存已就绪，
                // 后续等待线程复查必命中，保证同 Key 并发仅一次回源（防击穿）
                return cacheTemplate.getOrLoad(PERMISSION_CACHE_NAME, String.valueOf(userId),
                        properties.getTtl(), () -> String.join(",", loader.get()));
            });
        } catch (Exception ex) {
            // 锁获取异常（Redis 不可用等）→ 降级直查，不阻断权限判定（Wave 5.8 降级链一致）
            log.warn("权限缓存互斥回源降级直查, userId={}", userId, ex);
            return String.join(",", loader.get());
        }
    }

    /**
     * 失效单个用户的权限缓存（由 AFTER_COMMIT 监听器调用）
     *
     * @param userId 用户 ID；为 {@code null} 时不执行
     */
    public void evictUser(Long userId) {
        if (userId == null) {
            return;
        }
        evictUsers(Set.of(userId));
    }

    /**
     * 失效指定租户内用户的权限缓存（由 AFTER_COMMIT 监听器调用）。
     *
     * <p>成员绑定/解绑在平台租户上下文执行，目标租户与当前租户不同；必须由调用方显式传入
     * 受影响租户，不能依赖 {@code TenantContext}。</p>
     *
     * @param tenantId 受影响租户 ID；为 {@code null} 时不执行
     * @param userId 用户 ID；为 {@code null} 时不执行
     */
    public void evictUserInTenant(Long tenantId, Long userId) {
        if (tenantId == null || userId == null) {
            return;
        }
        cacheInvalidator.invalidateUsersByTenant(Set.of(
                new AuthPermissionCacheInvalidator.UserKey(tenantId, userId)));
    }

    /**
     * 批量失效用户权限缓存（去重，由 AFTER_COMMIT 监听器调用）
     *
     * <p>委托 {@link AuthPermissionCacheInvalidator}：按当前租户计算完整 Key 并批量删除，
     * 再经 Redisson RTopic 广播给其余实例；删除失败时以完整 Key 进入断连重对齐待处理集合。</p>
     *
     * @param userIds 用户 ID 集合；为空时不执行
     */
    public void evictUsers(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        Set<Long> distinctUserIds = new LinkedHashSet<>(userIds);
        cacheInvalidator.invalidateUsers(distinctUserIds);
    }

    /**
     * 失效某角色下全部用户的权限缓存（角色分配权限 / 角色删除后调用）
     *
     * @param roleId 角色 ID
     */
    public void evictByRole(Long roleId) {
        if (roleId == null) {
            return;
        }
        List<Long> userIds = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getRoleId, roleId))
                .stream().map(SysUserRole::getUserId).distinct().toList();
        evictUsers(userIds);
    }

    /**
     * 失效持有指定权限点的全部用户的权限缓存（权限点更新 / 删除后调用）。
     *
     * <p>权限点为平台共享数据，一次变更可能影响多个租户的角色与用户。此处按「权限 → 角色 → 用户」
     * 反查后，再用角色所属租户构造完整的跨租户 Key（{@code TenantKeys#cacheKey(Long, String, String)}），
     * 避免仅按当前租户（通常为平台租户）删除而遗漏其他租户的缓存。</p>
     *
     * @param permissionId 权限点 ID
     */
    public void evictByPermission(Long permissionId) {
        if (permissionId == null) {
            return;
        }
        List<Long> roleIds = rolePermissionMapper.selectList(new LambdaQueryWrapper<SysRolePermission>()
                        .eq(SysRolePermission::getPermissionId, permissionId))
                .stream().map(SysRolePermission::getRoleId).distinct().toList();
        if (roleIds.isEmpty()) {
            return;
        }
        Map<Long, Long> tenantByRoleId = roleMapper.selectTenantRefsByIds(roleIds).stream()
                .collect(Collectors.toMap(RoleTenantRef::getRoleId, RoleTenantRef::getTenantId,
                        (existing, ignored) -> existing));
        Set<AuthPermissionCacheInvalidator.UserKey> userKeys = new LinkedHashSet<>();
        userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .in(SysUserRole::getRoleId, roleIds))
                .forEach(relation -> {
                    Long tenantId = tenantByRoleId.get(relation.getRoleId());
                    if (tenantId != null && relation.getUserId() != null) {
                        userKeys.add(new AuthPermissionCacheInvalidator.UserKey(tenantId, relation.getUserId()));
                    }
                });
        if (userKeys.isEmpty()) {
            return;
        }
        cacheInvalidator.invalidateUsersByTenant(userKeys);
    }

    /**
     * 将权限码列表转为集合
     *
     * @param codes 权限码列表；为 {@code null} 或空时返回空集合
     * @return 权限码集合
     */
    private Set<String> toSet(List<String> codes) {
        return codes == null || codes.isEmpty() ? Set.of() : new LinkedHashSet<>(codes);
    }
}
