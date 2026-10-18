package com.bluewhale.common.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 缓存一致性模板单测：覆盖命中缓存、未命中回源并写入、空值哨兵、TTL 抖动范围、
 * evict / 延迟双删、Redis 异常降级回源。
 *
 * @author BlueWhale
 */
class CacheConsistencyTemplateTest {

    /** Key 约定 {@code whale:cache:user:1} */
    private static final String KEY = "whale:cache:user:1";

    /** 空值哨兵默认值 */
    private static final String NULL_SENTINEL = "__NULL__";

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

    private final ValueOperations<String, String> valueOperations = mockValueOperations();

    private final CacheProperties properties = new CacheProperties();

    private final CacheConsistencyTemplate template = new CacheConsistencyTemplate(redisTemplate, properties);

    /**
     * 注入 StringRedisTemplate 的字符串操作视图
     */
    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    /**
     * 命中缓存：直接返回缓存值且不回源
     */
    @Test
    void shouldReturnCachedValueOnHit() {
        when(valueOperations.get(KEY)).thenReturn("cached");
        Supplier<String> loader = supplier("loaded");

        String result = template.getOrLoad("user", "1", Duration.ofMinutes(5), loader);

        assertThat(result).isEqualTo("cached");
        verify(loader, never()).get();
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    /**
     * 未命中：回源加载并按 Key 约定写入缓存，TTL 落在抖动区间内
     */
    @Test
    void shouldLoadAndCacheOnMiss() {
        when(valueOperations.get(KEY)).thenReturn(null);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);

        String result = template.getOrLoad("user", "1", Duration.ofMinutes(5), supplier("loaded"));

        assertThat(result).isEqualTo("loaded");
        verify(valueOperations).set(eq(KEY), eq("loaded"), ttlCaptor.capture());
        assertThat(ttlCaptor.getValue().toMillis()).isBetween(270_000L, 330_000L);
    }

    /**
     * 空值防穿透：空结果写入哨兵短 TTL，后续读取命中哨兵不再回源
     */
    @Test
    void shouldWriteSentinelForNullResultAndNotLoadAgain() {
        when(valueOperations.get(KEY)).thenReturn(null, NULL_SENTINEL);
        Supplier<String> loader = supplier(null);

        String first = template.getOrLoad("user", "1", Duration.ofMinutes(5), loader);
        String second = template.getOrLoad("user", "1", Duration.ofMinutes(5), loader);

        assertThat(first).isNull();
        assertThat(second).isNull();
        verify(valueOperations).set(eq(KEY), eq(NULL_SENTINEL), eq(Duration.ofSeconds(60)));
        verify(loader, times(1)).get();
    }

    /**
     * 防雪崩：正常值 TTL 抖动落在 ±{@code ttlJitterPercent}% 区间内
     */
    @Test
    void shouldApplyTtlJitterWithinConfiguredRange() {
        when(valueOperations.get(KEY)).thenReturn(null);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);

        for (int i = 0; i < 20; i++) {
            template.getOrLoad("user", "1", Duration.ofSeconds(100), supplier("v"));
        }

        verify(valueOperations, times(20)).set(eq(KEY), eq("v"), ttlCaptor.capture());
        List<Duration> ttls = ttlCaptor.getAllValues();
        assertThat(ttls).hasSize(20);
        assertThat(ttls).allSatisfy(ttl -> assertThat(ttl.toMillis()).isBetween(90_000L, 110_000L));
    }

    /**
     * 立即删除：按 Key 约定删除缓存 Key
     */
    @Test
    void shouldEvictKey() {
        template.evict("user", "1");

        verify(redisTemplate).delete(KEY);
    }

    /**
     * 延迟双删：立即删除一次，延迟后再次删除
     */
    @Test
    void shouldEvictTwiceWithDelay() {
        template.evictWithDelay("user", "1", Duration.ofMillis(150));

        verify(redisTemplate).delete(KEY);
        verify(redisTemplate, timeout(1000).times(2)).delete(KEY);
    }

    /**
     * 读异常降级：Redis 读失败时直接回源，写失败被忽略且不阻断主流程
     */
    @Test
    void shouldFallbackToLoaderWhenRedisReadFails() {
        when(valueOperations.get(KEY)).thenThrow(new IllegalStateException("redis down"));
        doThrow(new IllegalStateException("redis down"))
                .when(valueOperations).set(anyString(), anyString(), any(Duration.class));

        String result = template.getOrLoad("user", "1", Duration.ofMinutes(5), supplier("loaded"));

        assertThat(result).isEqualTo("loaded");
    }

    /**
     * 删异常降级：Redis 删除失败仅告警，不抛出异常
     */
    @Test
    void shouldIgnoreDeleteFailureWhenEvicting() {
        when(redisTemplate.delete(KEY)).thenThrow(new IllegalStateException("redis down"));

        template.evict("user", "1");

        verify(redisTemplate).delete(KEY);
    }

    /**
     * 模拟字符串操作视图
     *
     * @return 值操作 Mock
     */
    @SuppressWarnings("unchecked")
    private static ValueOperations<String, String> mockValueOperations() {
        return mock(ValueOperations.class);
    }

    /**
     * 构造固定返回值的回源加载器
     *
     * @param value 加载器返回值，可为 {@code null}
     * @return 回源加载器 Mock
     */
    @SuppressWarnings("unchecked")
    private static Supplier<String> supplier(String value) {
        Supplier<String> supplier = mock(Supplier.class);
        when(supplier.get()).thenReturn(value);
        return supplier;
    }
}
