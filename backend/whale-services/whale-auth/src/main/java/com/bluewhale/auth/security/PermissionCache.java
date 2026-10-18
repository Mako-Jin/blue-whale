package com.bluewhale.auth.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.auth.config.PermissionCacheProperties;
import com.bluewhale.auth.entity.SysRolePermission;
import com.bluewhale.auth.entity.SysUserRole;
import com.bluewhale.auth.mapper.SysRolePermissionMapper;
import com.bluewhale.auth.mapper.SysUserRoleMapper;
import com.bluewhale.common.cache.CacheConsistencyTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 权限判定缓存（Cache-Aside，REQ-DIST-003 场景接入）。
 * <p>读路径：{@link #getPermissionCodes(Long, Supplier)} 命中缓存直接返回，未命中回源并写缓存
 * （空集合写空串，读回空串即空集合）；Redis 异常由 {@link CacheConsistencyTemplate} 内部降级直查，
 * 不阻断主流程。</p>
 * <p>写路径：{@link #evictUser(Long)} / {@link #evictUsers(Collection)} / {@link #evictByRole(Long)} /
 * {@link #evictByPermission(Long)} 统一在事务提交后失效受影响用户的缓存，避免「事务未提交即删缓存」
 * 的脏缓存窗口。</p>
 * <p>本类不注入 {@code SysRoleService}：读路径的 DB 查询由调用方以 {@link Supplier} 传入，
 * 失效路径的受影响用户反查直接走 Mapper，避免 {@code SysRoleServiceImpl → PermissionCache →
 * SysRoleService} 循环依赖，也不复制权限码查询逻辑。</p>
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
    private final SysUserRoleMapper userRoleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;

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
     * 失效单个用户的权限缓存（事务提交后执行）
     *
     * @param userId 用户 ID
     */
    public void evictUser(Long userId) {
        afterCommit(() -> cacheTemplate.evict(PERMISSION_CACHE_NAME, String.valueOf(userId)));
    }

    /**
     * 批量失效用户权限缓存（去重，事务提交后执行）
     *
     * @param userIds 用户 ID 集合；为空时不执行
     */
    public void evictUsers(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        Set<Long> distinctUserIds = new LinkedHashSet<>(userIds);
        afterCommit(() -> {
            for (Long userId : distinctUserIds) {
                cacheTemplate.evict(PERMISSION_CACHE_NAME, String.valueOf(userId));
            }
        });
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
     * 失效持有指定权限点的全部用户的权限缓存（权限点更新 / 删除后调用）
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
        List<Long> userIds = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .in(SysUserRole::getRoleId, roleIds))
                .stream().map(SysUserRole::getUserId).distinct().toList();
        evictUsers(userIds);
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

    /**
     * 事务同步激活时注册 {@code afterCommit} 回调，否则立即执行
     *
     * @param action 待执行的失效动作
     */
    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
