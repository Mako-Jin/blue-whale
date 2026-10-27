package com.bluewhale.auth.security;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.auth.config.PermissionCacheProperties;
import com.bluewhale.auth.entity.RoleTenantRef;
import com.bluewhale.auth.entity.SysRolePermission;
import com.bluewhale.auth.entity.SysUserRole;
import com.bluewhale.auth.infrastructure.cache.AuthPermissionCacheInvalidator;
import com.bluewhale.auth.mapper.SysRoleMapper;
import com.bluewhale.auth.mapper.SysRolePermissionMapper;
import com.bluewhale.auth.mapper.SysUserRoleMapper;
import com.bluewhale.common.cache.CacheConsistencyTemplate;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link PermissionCache} 单元测试：读路径（命中/回源/空结果/禁用）、失效路径
 * （用户/角色/权限点反查）与事务提交后失效（AUTH-20 / REQ-DIST-003）。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class PermissionCacheTest {

    private static final long USER_ID = 7L;
    private static final long ROLE_ID = 3L;
    private static final long PERM_ID = 10L;
    private static final Duration TTL = Duration.ofMinutes(5);

    @Mock
    private CacheConsistencyTemplate cacheTemplate;

    @Mock
    private PermissionCacheProperties properties;

    @Mock
    private PermissionCacheMutex mutex;

    @Mock
    private AuthPermissionCacheInvalidator cacheInvalidator;

    @Mock
    private SysUserRoleMapper userRoleMapper;

    @Mock
    private SysRolePermissionMapper rolePermissionMapper;

    @Mock
    private SysRoleMapper roleMapper;

    private PermissionCache permissionCache;

    /**
     * 注册实体元信息缓存：`LambdaQueryWrapper` 按实体解析列名，纯单测无 MyBatis 环境需手动初始化 TableInfo。
     */
    @BeforeAll
    static void initTableInfoCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysUserRole.class);
        TableInfoHelper.initTableInfo(assistant, SysRolePermission.class);
    }

    @BeforeEach
    void setUp() {
        permissionCache = new PermissionCache(cacheTemplate, properties, mutex, cacheInvalidator,
                userRoleMapper, rolePermissionMapper, roleMapper);
    }

    /**
     * 令互斥门面透传 loader（模拟切面正常加锁执行），供未命中路径用例复用
     */
    @SuppressWarnings("unchecked")
    private void stubMutexPassThrough() {
        when(mutex.load(any(), any())).thenAnswer(inv -> ((Supplier<String>) inv.getArgument(1)).get());
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static SysUserRole userRole(Long userId, Long roleId) {
        SysUserRole relation = new SysUserRole();
        relation.setUserId(userId);
        relation.setRoleId(roleId);
        return relation;
    }

    private static SysRolePermission rolePermission(Long roleId, Long permissionId) {
        SysRolePermission relation = new SysRolePermission();
        relation.setRoleId(roleId);
        relation.setPermissionId(permissionId);
        return relation;
    }

    private static RoleTenantRef roleTenantRef(Long roleId, Long tenantId) {
        RoleTenantRef ref = new RoleTenantRef();
        ref.setRoleId(roleId);
        ref.setTenantId(tenantId);
        return ref;
    }

    @Test
    @DisplayName("enabled=false：不读缓存，直接回源并返回 loader 集合")
    void shouldLoadDirectlyWhenDisabled() {
        when(properties.isEnabled()).thenReturn(false);

        Set<String> codes = permissionCache.getPermissionCodes(USER_ID, () -> List.of("a", "b"));

        assertEquals(Set.of("a", "b"), codes);
        verifyNoInteractions(cacheTemplate, mutex);
    }

    @Test
    @DisplayName("enabled=false：loader 返回空集合时返回空集合")
    void shouldReturnEmptyWhenDisabledAndLoaderEmpty() {
        when(properties.isEnabled()).thenReturn(false);

        Set<String> codes = permissionCache.getPermissionCodes(USER_ID, List::of);

        assertTrue(codes.isEmpty());
    }

    @Test
    @DisplayName("enabled=false：loader 返回 null 时返回空集合")
    void shouldReturnEmptyWhenDisabledAndLoaderNull() {
        when(properties.isEnabled()).thenReturn(false);

        Set<String> codes = permissionCache.getPermissionCodes(USER_ID, () -> null);

        assertTrue(codes.isEmpty());
    }

    @Test
    @DisplayName("缓存命中：模板返回值切分为权限码集合")
    void shouldReturnCodesWhenCacheHit() {
        when(properties.isEnabled()).thenReturn(true);
        when(cacheTemplate.getOrLoad(any(), any(), any(), any())).thenReturn("a,b");

        Set<String> codes = permissionCache.getPermissionCodes(USER_ID, () -> List.of("x"));

        assertEquals(Set.of("a", "b"), codes);
    }

    @Test
    @DisplayName("缓存命中空结果哨兵：模板返回空串时返回空集合")
    void shouldReturnEmptyWhenCacheHitEmptyString() {
        when(properties.isEnabled()).thenReturn(true);
        when(cacheTemplate.getOrLoad(any(), any(), any(), any())).thenReturn("");

        Set<String> codes = permissionCache.getPermissionCodes(USER_ID, () -> List.of("x"));

        assertTrue(codes.isEmpty());
    }

    @Test
    @DisplayName("缓存命中空值：模板返回 null 时返回空集合")
    void shouldReturnEmptyWhenCacheHitNull() {
        when(properties.isEnabled()).thenReturn(true);
        when(cacheTemplate.getOrLoad(any(), any(), any(), any())).thenReturn(null);

        Set<String> codes = permissionCache.getPermissionCodes(USER_ID, () -> List.of("x"));

        assertTrue(codes.isEmpty());
    }

    @Test
    @DisplayName("缓存命中：含重复权限码时不抛异常且去重")
    void shouldDeduplicateWhenCacheHitDuplicate() {
        when(properties.isEnabled()).thenReturn(true);
        when(cacheTemplate.getOrLoad(any(), any(), any(), any())).thenReturn("a,a,b");

        Set<String> codes = permissionCache.getPermissionCodes(USER_ID, () -> List.of("x"));

        assertEquals(2, codes.size());
        assertTrue(codes.containsAll(Set.of("a", "b")));
    }

    @Test
    @DisplayName("缓存未命中：以逗号拼接串回源写缓存，name/id/ttl 实参正确")
    void shouldLoadAndCacheWhenMiss() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getTtl()).thenReturn(TTL);
        stubMutexPassThrough();
        AtomicReference<String> joinedValue = new AtomicReference<>();
        when(cacheTemplate.getOrLoad(eq(PermissionCache.PERMISSION_CACHE_NAME), eq(String.valueOf(USER_ID)), eq(TTL), any()))
                .thenAnswer(invocation -> {
                    Supplier<String> loader = invocation.getArgument(3);
                    joinedValue.set(loader.get());
                    return joinedValue.get();
                });

        Set<String> codes = permissionCache.getPermissionCodes(USER_ID, () -> List.of("a", "b"));

        assertEquals(Set.of("a", "b"), codes);
        assertEquals("a,b", joinedValue.get());
        // 外层 getOrLoad + 锁内复查回源 getOrLoad，共 2 次
        verify(cacheTemplate, times(2))
                .getOrLoad(eq(PermissionCache.PERMISSION_CACHE_NAME), eq(String.valueOf(USER_ID)), eq(TTL), any());
        verify(mutex).load(eq(USER_ID), any());
    }

    @Test
    @DisplayName("缓存命中：不调用互斥门面（防击穿路径不取锁）")
    void shouldNotUseMutexWhenCacheHit() {
        when(properties.isEnabled()).thenReturn(true);
        when(cacheTemplate.getOrLoad(any(), any(), any(), any())).thenReturn("a,b");

        Set<String> codes = permissionCache.getPermissionCodes(USER_ID, () -> List.of("x"));

        assertEquals(Set.of("a", "b"), codes);
        verifyNoInteractions(mutex);
    }

    @Test
    @DisplayName("未命中：锁内复查缓存命中，则 loader 不被调用（同 Key 并发仅一次回源）")
    void shouldSkipLoaderWhenRecheckHits() {
        when(properties.isEnabled()).thenReturn(true);
        stubMutexPassThrough();
        when(cacheTemplate.getOrLoad(any(), any(), any(), any())).thenAnswer(inv -> {
            Supplier<String> reload = inv.getArgument(3);
            return reload.get();
        });
        when(cacheTemplate.getIfPresent(any(), any())).thenReturn("cached,a");
        AtomicInteger loadCount = new AtomicInteger();

        Set<String> codes = permissionCache.getPermissionCodes(USER_ID, () -> {
            loadCount.incrementAndGet();
            return List.of("x");
        });

        assertEquals(Set.of("cached", "a"), codes);
        assertEquals(0, loadCount.get());
    }

    @Test
    @DisplayName("锁异常：降级直查且仍返回 loader 结果，不阻断权限判定")
    void shouldFallbackToLoaderWhenMutexFails() {
        when(properties.isEnabled()).thenReturn(true);
        when(cacheTemplate.getOrLoad(any(), any(), any(), any())).thenAnswer(inv -> {
            Supplier<String> reload = inv.getArgument(3);
            return reload.get();
        });
        when(mutex.load(any(), any())).thenThrow(new BizException(ErrorCode.LOCK_ACQUIRE_FAILED));

        Set<String> codes = permissionCache.getPermissionCodes(USER_ID, () -> List.of("a", "b"));

        assertEquals(Set.of("a", "b"), codes);
    }

    @Test
    @DisplayName("evictUser：无事务同步时立即失效该用户缓存")
    void shouldEvictUserImmediatelyWhenNoSynchronization() {
        permissionCache.evictUser(USER_ID);

        verify(cacheInvalidator).invalidateUsers(Set.of(USER_ID));
    }

    @Test
    @DisplayName("evictUsers：null 集合不触发失效")
    void shouldSkipEvictWhenUserIdsNull() {
        permissionCache.evictUsers(null);

        verifyNoInteractions(cacheInvalidator);
    }

    @Test
    @DisplayName("evictUsers：空集合不触发失效")
    void shouldSkipEvictWhenUserIdsEmpty() {
        permissionCache.evictUsers(List.of());

        verifyNoInteractions(cacheInvalidator);
    }

    @Test
    @DisplayName("evictUsers：去重后批量失效")
    void shouldEvictEachDistinctUser() {
        permissionCache.evictUsers(List.of(1L, 1L, 2L));

        verify(cacheInvalidator).invalidateUsers(Set.of(1L, 2L));
    }

    @Test
    @DisplayName("evictByRole：roleId 为 null 时无任何调用")
    void shouldSkipEvictByRoleWhenNull() {
        permissionCache.evictByRole(null);

        verifyNoInteractions(userRoleMapper, cacheInvalidator);
    }

    @Test
    @DisplayName("evictByRole：按角色反查用户并去重批量失效")
    void shouldEvictUsersByRole() {
        when(userRoleMapper.selectList(any())).thenReturn(List.of(
                userRole(1L, ROLE_ID), userRole(1L, ROLE_ID), userRole(2L, ROLE_ID)));

        permissionCache.evictByRole(ROLE_ID);

        verify(cacheInvalidator).invalidateUsers(Set.of(1L, 2L));
    }

    @Test
    @DisplayName("evictByRole：角色下无用户时不触发失效")
    void shouldSkipEvictByRoleWhenNoUsers() {
        when(userRoleMapper.selectList(any())).thenReturn(List.of());

        permissionCache.evictByRole(ROLE_ID);

        verifyNoInteractions(cacheInvalidator);
    }

    @Test
    @DisplayName("evictByPermission：permissionId 为 null 时无任何调用")
    void shouldSkipEvictByPermissionWhenNull() {
        permissionCache.evictByPermission(null);

        verifyNoInteractions(rolePermissionMapper, userRoleMapper, roleMapper, cacheInvalidator);
    }

    @Test
    @DisplayName("evictByPermission：无关联角色时不发起 in 查询、不触发失效")
    void shouldSkipInQueryWhenNoRoles() {
        when(rolePermissionMapper.selectList(any())).thenReturn(List.of());

        permissionCache.evictByPermission(PERM_ID);

        verify(userRoleMapper, never()).selectList(any());
        verifyNoInteractions(roleMapper, cacheInvalidator);
    }

    @Test
    @DisplayName("evictByPermission：权限→角色→用户两级反查后按角色所属租户跨租户失效")
    void shouldEvictUsersByPermission() {
        when(rolePermissionMapper.selectList(any())).thenReturn(List.of(rolePermission(ROLE_ID, PERM_ID)));
        when(roleMapper.selectTenantRefsByIds(List.of(ROLE_ID))).thenReturn(List.of(roleTenantRef(ROLE_ID, 9L)));
        when(userRoleMapper.selectList(any())).thenReturn(List.of(userRole(USER_ID, ROLE_ID)));

        permissionCache.evictByPermission(PERM_ID);

        verify(cacheInvalidator).invalidateUsersByTenant(
                Set.of(new AuthPermissionCacheInvalidator.UserKey(9L, USER_ID)));
        verify(cacheInvalidator, never()).invalidateUsers(any());
    }

    @Test
    @DisplayName("evictByPermission：角色租户无法定位时不触发失效")
    void shouldSkipEvictByPermissionWhenRoleTenantMissing() {
        when(rolePermissionMapper.selectList(any())).thenReturn(List.of(rolePermission(ROLE_ID, PERM_ID)));
        when(roleMapper.selectTenantRefsByIds(List.of(ROLE_ID))).thenReturn(List.of());
        when(userRoleMapper.selectList(any())).thenReturn(List.of(userRole(USER_ID, ROLE_ID)));

        permissionCache.evictByPermission(PERM_ID);

        verifyNoInteractions(cacheInvalidator);
    }

    @Test
    @DisplayName("afterCommit：事务同步激活时注册回调，提交后才失效")
    void shouldDeferEvictUntilAfterCommitWhenSynchronizationActive() {
        TransactionSynchronizationManager.initSynchronization();

        permissionCache.evictUser(USER_ID);
        verifyNoInteractions(cacheInvalidator);

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        verify(cacheInvalidator).invalidateUsers(Set.of(USER_ID));
    }
}
