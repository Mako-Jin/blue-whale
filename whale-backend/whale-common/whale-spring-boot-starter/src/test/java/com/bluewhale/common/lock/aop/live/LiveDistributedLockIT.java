package com.bluewhale.common.lock.aop.live;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.lock.annotation.DistributedLock;
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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 分布式锁的实时集成测试（真实 Redisson 直连 Redis @ 127.0.0.1:6379）。
 *
 * <p><b>默认跳过</b>：仅在系统属性 {@code -Dwhale.live.it=true} 时执行。</p>
 * <p>执行命令（cwd {@code f:\blue-whale\backend}）：</p>
 * <pre>
 * mvn -B -pl whale-common/whale-spring-boot-starter -am test -Dwhale.live.it=true -Dtest=LiveDistributedLockIT -DfailIfNoTests=false
 * </pre>
 *
 * @author BlueWhale
 */
@EnabledIfSystemProperty(named = "whale.live.it", matches = "true")
class LiveDistributedLockIT {

    private static final String REDIS_ADDRESS = "redis://127.0.0.1:6379";
    private static final String EXPECTED_LOCK_KEY = "lock:live:test:1";
    private static final long AWAIT_SECONDS = 5L;

    private static RedissonClient redissonClient;
    private static LockSampleService sampleService;

    @BeforeAll
    static void setUpClass() {
        Config config = new Config();
        config.useSingleServer().setAddress(REDIS_ADDRESS);
        redissonClient = Redisson.create(config);
        DistributedLockAspect aspect = new DistributedLockAspect(redissonClient);
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(new LockSampleService());
        proxyFactory.addAspect(aspect);
        sampleService = proxyFactory.getProxy();
    }

    @AfterAll
    static void tearDownClass() {
        if (redissonClient != null) {
            redissonClient.shutdown();
        }
    }

    @Test
    @DisplayName("同 key 并发：持锁期间第二个调用快速失败，抛 BizException code=429")
    void shouldRejectConcurrentInvocationOnSameKey() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> holder = pool.submit(() -> sampleService.slowWork(1L, entered, release));
            assertThat(entered.await(AWAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
            // 持锁期间：锁在 Redis 中确实被持有
            assertThat(redissonClient.getLock(EXPECTED_LOCK_KEY).isLocked()).isTrue();

            assertThatThrownBy(() -> sampleService.slowWork(1L, new CountDownLatch(0), new CountDownLatch(0)))
                    .isInstanceOf(BizException.class)
                    .satisfies(ex -> assertThat(((BizException) ex).getCode())
                            .isEqualTo(ErrorCode.LOCK_ACQUIRE_FAILED.getCode()));

            release.countDown();
            assertThat(holder.get(AWAIT_SECONDS, TimeUnit.SECONDS)).isEqualTo("done-1");
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("串行可获取；锁释放后同 key 可再次获取")
    void shouldAllowReAcquireAfterLockReleased() {
        assertThat(sampleService.slowWork(2L, new CountDownLatch(0), new CountDownLatch(0))).isEqualTo("done-2");
        assertThat(sampleService.slowWork(2L, new CountDownLatch(0), new CountDownLatch(0))).isEqualTo("done-2");
    }

    /** 被切面代理的样例服务：key 由 SpEL {@code #id} 解析 */
    public static class LockSampleService {

        @DistributedLock(prefix = "lock", biz = "live:test", key = "#id",
                waitTime = 0, leaseTime = 30, failFast = true)
        public String slowWork(Long id, CountDownLatch entered, CountDownLatch release) {
            entered.countDown();
            try {
                release.await(AWAIT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "done-" + id;
        }
    }
}
