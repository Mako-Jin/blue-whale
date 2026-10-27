package com.bluewhale.auth.live;

import com.bluewhale.auth.application.event.DomainEventPublisher;
import com.bluewhale.auth.application.role.RoleAppService;
import com.bluewhale.auth.application.role.command.AssignRolePermissionsCommand;
import com.bluewhale.auth.config.PermissionCacheProperties;
import com.bluewhale.auth.domain.model.vo.RoleCode;
import com.bluewhale.auth.domain.permission.repository.PermissionRepository;
import com.bluewhale.auth.domain.role.aggregate.Role;
import com.bluewhale.auth.domain.role.repository.RoleRepository;
import com.bluewhale.auth.infrastructure.cache.AuthPermissionCacheInvalidator;
import com.bluewhale.auth.mapper.SysRolePermissionMapper;
import com.bluewhale.auth.mapper.SysUserRoleMapper;
import com.bluewhale.auth.security.PermissionCache;
import com.bluewhale.auth.security.PermissionCacheMutex;
import com.bluewhale.common.cache.CacheConsistencyTemplate;
import com.bluewhale.common.cache.CacheProperties;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.lock.aop.DistributedLockAspect;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 权限中心分布式锁场景的实时集成测试（真实 Redis 5.x @ 127.0.0.1:6379）。
 *
 * <p>覆盖两个场景：① 角色权限变更互斥（{@code lock:auth:role:{roleId}}）；② 缓存重建防击穿
 * （{@code lock:cache:auth:user:perm:{userId}}，同 Key 并发仅一次回源）。切面以
 * {@link AspectJProxyFactory} 手工织入 {@link DistributedLockAspect}（直连 Redisson）。</p>
 *
 * <p><b>默认跳过</b>：仅在系统属性 {@code -Dwhale.live.it=true} 时执行。</p>
 * <p>执行命令（cwd {@code f:\blue-whale\backend}）：</p>
 * <pre>
 * mvn -B -pl whale-services/whale-service-auth/whale-service-auth-starter -am test -Dwhale.live.it=true -Dtest=LivePermissionLockIT -DfailIfNoTests=false
 * </pre>
 *
 * @author BlueWhale
 */
@EnabledIfSystemProperty(named = "whale.live.it", matches = "true")
class LivePermissionLockIT {

    private static final String REDIS_ADDRESS = "redis://127.0.0.1:6379";
    private static final String REDIS_HOST = "127.0.0.1";
    private static final int REDIS_PORT = 6379;
    private static final long ROLE_ID = 71L;
    private static final long USER_ID = 91_001L;
    private static final int CONCURRENCY = 8;
    private static final long AWAIT_SECONDS = 5L;
    private static final long LOADER_DELAY_MILLIS = 30L;
    private static final String EXPECTED_ROLE_LOCK_KEY = "whale:1:lock:auth:role:" + ROLE_ID;

    private static RedissonClient redissonClient;
    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static DistributedLockAspect aspect;

    @BeforeAll
    static void setUpClass() {
        Config config = new Config();
        config.useSingleServer().setAddress(REDIS_ADDRESS);
        redissonClient = Redisson.create(config);
        aspect = new DistributedLockAspect(redissonClient);

        connectionFactory = new LettuceConnectionFactory(REDIS_HOST, REDIS_PORT);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    @AfterAll
    static void tearDownClass() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
        if (redissonClient != null) {
            redissonClient.shutdown();
        }
    }

    @Test
    @DisplayName("场景①同角色并发：持锁期间第二调用快速失败 LOCK_ACQUIRE_FAILED，Redis 中锁确实被持有")
    void shouldRejectConcurrentAssignPermissionsOnSameRole() throws Exception {
        TenantContext.set(1L);
        RoleRepository roleRepository = mock(RoleRepository.class);
        PermissionRepository permissionRepository = mock(PermissionRepository.class);
        DomainEventPublisher eventPublisher = mock(DomainEventPublisher.class);

        Role role = Role.reconstitute(ROLE_ID, 1L, RoleCode.of("it_role"), "实时IT角色", null, 0,
                Role.STATUS_ENABLED, null, null);
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        // 非空权限列表 → 权限 ID 校验处阻塞，模拟「持锁下的慢事务」
        when(permissionRepository.countByIds(any())).thenAnswer(inv -> {
            entered.countDown();
            release.await(AWAIT_SECONDS, TimeUnit.SECONDS);
            return 1L;
        });

        RoleAppService target = new RoleAppService(roleRepository, permissionRepository, eventPublisher);

        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAspect(aspect);
        RoleAppService proxy = proxyFactory.getProxy();

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> holder = pool.submit(() -> proxy.assignPermissions(ROLE_ID,
                    new AssignRolePermissionsCommand(List.of(10L))));
            assertThat(entered.await(AWAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
            // 持锁期间：锁在 Redis 中确实被持有
            assertThat(redissonClient.getLock(EXPECTED_ROLE_LOCK_KEY).isLocked()).isTrue();

            assertThatThrownBy(() -> proxy.assignPermissions(ROLE_ID,
                    new AssignRolePermissionsCommand(List.of(10L))))
                    .isInstanceOf(BizException.class)
                    .satisfies(ex -> assertThat(((BizException) ex).getCode())
                            .isEqualTo(ErrorCode.LOCK_ACQUIRE_FAILED.getCode()));

            release.countDown();
            holder.get(AWAIT_SECONDS, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            pool.shutdownNow();
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("场景②缓存防击穿：N 线程并发未命中，DB 回源计数 == 1")
    void shouldLoadOnceUnderConcurrentCacheMiss() throws Exception {
        CacheConsistencyTemplate template = new CacheConsistencyTemplate(redisTemplate, new CacheProperties());

        AspectJProxyFactory mutexFactory = new AspectJProxyFactory(new PermissionCacheMutex());
        mutexFactory.addAspect(aspect);
        PermissionCacheMutex mutex = mutexFactory.getProxy();

        PermissionCache cache = new PermissionCache(template, new PermissionCacheProperties(), mutex,
                mock(AuthPermissionCacheInvalidator.class),
                mock(SysUserRoleMapper.class), mock(SysRolePermissionMapper.class));

        // 清理可能残留的缓存键，保证从「未命中」开始
        template.evict(PermissionCache.PERMISSION_CACHE_NAME, String.valueOf(USER_ID));

        AtomicInteger loadCount = new AtomicInteger();
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY);
        List<Future<Set<String>>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < CONCURRENCY; i++) {
                futures.add(pool.submit(() -> {
                    startGate.await(AWAIT_SECONDS, TimeUnit.SECONDS);
                    return cache.getPermissionCodes(USER_ID, () -> {
                        loadCount.incrementAndGet();
                        sleepQuietly();
                        return List.of("p1", "p2");
                    });
                }));
            }
            startGate.countDown();
            for (Future<Set<String>> future : futures) {
                assertThat(future.get(AWAIT_SECONDS * 2, TimeUnit.SECONDS)).containsExactlyInAnyOrder("p1", "p2");
            }
        } finally {
            pool.shutdownNow();
            template.evict(PermissionCache.PERMISSION_CACHE_NAME, String.valueOf(USER_ID));
        }

        assertThat(loadCount.get()).isEqualTo(1);
    }

    /**
     * 模拟 DB 回源耗时（持锁期间），使并发线程均进入锁等待
     */
    private static void sleepQuietly() {
        try {
            Thread.sleep(LOADER_DELAY_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
