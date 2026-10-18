package com.bluewhale.common.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 缓存一致性模板（Cache-Aside，字符串值语义）。
 * <p>提供 {@link #getOrLoad(String, String, Duration, Supplier)}（未命中回源 + 空值哨兵防穿透 +
 * TTL 抖动防雪崩）、{@link #evict(String, String)} 与 {@link #evictWithDelay(String, String, Duration)}
 * （延迟双删）。Key 约定 {@code {keyPrefix}:{name}:{id}}。</p>
 * <p>Redis 任何异常均降级处理：读失败直接回源，写/删失败仅告警忽略，不阻断业务主流程。
 * 击穿防护（同 Key 并发仅一次回源）由 {@code @DistributedLock} 场景接入承担，不在本工具内实现。</p>
 *
 * @author BlueWhale
 */
@Slf4j
public class CacheConsistencyTemplate {

    /** Key 分段分隔符 */
    private static final String KEY_SEPARATOR = ":";

    /** 延迟双删线程名，便于问题定位 */
    private static final String EVICT_THREAD_NAME = "whale-cache-evict";

    private final StringRedisTemplate redisTemplate;

    private final CacheProperties properties;

    /** 延迟双删调度器，守护线程，避免阻塞 JVM 退出 */
    private final ScheduledExecutorService scheduler;

    /**
     * 构造缓存一致性模板
     *
     * @param redisTemplate Redis 字符串操作模板
     * @param properties 缓存配置属性
     */
    public CacheConsistencyTemplate(StringRedisTemplate redisTemplate, CacheProperties properties) {
        this(redisTemplate, properties, createScheduler());
    }

    /**
     * 构造缓存一致性模板（可注入调度器，便于测试）
     *
     * @param redisTemplate Redis 字符串操作模板
     * @param properties 缓存配置属性
     * @param scheduler 延迟双删调度器
     */
    CacheConsistencyTemplate(StringRedisTemplate redisTemplate, CacheProperties properties,
                             ScheduledExecutorService scheduler) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.scheduler = scheduler;
    }

    /**
     * 读取缓存，未命中则回源加载并写入缓存
     *
     * @param name 缓存名称（业务域），如 {@code user}
     * @param id 缓存标识，如用户主键
     * @param ttl 正常值的存活时长（写入时叠加随机抖动）
     * @param loader 回源加载器，返回空表示数据不存在
     * @return 缓存值；空结果返回 {@code null}
     */
    public String getOrLoad(String name, String id, Duration ttl, Supplier<String> loader) {
        String key = buildKey(name, id);
        String cached = readQuietly(key);
        if (cached != null) {
            return properties.getNullSentinel().equals(cached) ? null : cached;
        }
        String loaded = loader.get();
        if (loaded == null) {
            writeQuietly(key, properties.getNullSentinel(), properties.getNullTtl());
            return null;
        }
        writeQuietly(key, loaded, applyJitter(ttl));
        return loaded;
    }

    /**
     * 立即删除缓存 Key
     *
     * @param name 缓存名称（业务域）
     * @param id 缓存标识
     */
    public void evict(String name, String id) {
        deleteQuietly(buildKey(name, id));
    }

    /**
     * 延迟双删：立即删除一次，延迟后再删除一次，覆盖「先删缓存再更新库」的并发窗口
     *
     * @param name 缓存名称（业务域）
     * @param id 缓存标识
     * @param delayMillis 第二次删除的延迟时长
     */
    public void evictWithDelay(String name, String id, Duration delayMillis) {
        String key = buildKey(name, id);
        deleteQuietly(key);
        long delay = delayMillis == null ? 0L : delayMillis.toMillis();
        try {
            scheduler.schedule(() -> deleteQuietly(key), delay, TimeUnit.MILLISECONDS);
        } catch (Exception ex) {
            log.warn("延迟双删调度失败，已执行首次删除: key={}, error={}", key, ex.getMessage());
        }
    }

    /**
     * 读取缓存，异常时降级返回 {@code null}（触发回源）
     *
     * @param key Redis Key
     * @return 缓存值；未命中或异常时返回 {@code null}
     */
    private String readQuietly(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception ex) {
            log.warn("Redis 读取失败，降级回源: key={}, error={}", key, ex.getMessage());
            return null;
        }
    }

    /**
     * 写入缓存，异常时仅告警忽略
     *
     * @param key Redis Key
     * @param value 缓存值
     * @param ttl 存活时长
     */
    private void writeQuietly(String key, String value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
        } catch (Exception ex) {
            log.warn("Redis 写入失败，忽略: key={}, error={}", key, ex.getMessage());
        }
    }

    /**
     * 删除缓存 Key，异常时仅告警忽略
     *
     * @param key Redis Key
     */
    private void deleteQuietly(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception ex) {
            log.warn("Redis 删除失败，忽略: key={}, error={}", key, ex.getMessage());
        }
    }

    /**
     * 拼接缓存 Key：{@code {keyPrefix}:{name}:{id}}
     *
     * @param name 缓存名称（业务域）
     * @param id 缓存标识
     * @return Redis Key
     */
    private String buildKey(String name, String id) {
        return properties.getKeyPrefix() + KEY_SEPARATOR + name + KEY_SEPARATOR + id;
    }

    /**
     * 为 TTL 叠加 ±{@code ttlJitterPercent}% 的随机抖动，防缓存雪崩
     *
     * @param ttl 基础存活时长
     * @return 抖动后的存活时长；配置为 0 或入参非法时原样返回
     */
    private Duration applyJitter(Duration ttl) {
        int percent = properties.getTtlJitterPercent();
        if (percent <= 0 || ttl == null || ttl.isZero() || ttl.isNegative()) {
            return ttl;
        }
        long baseMillis = ttl.toMillis();
        long delta = baseMillis * percent / 100;
        if (delta <= 0) {
            return ttl;
        }
        long offset = ThreadLocalRandom.current().nextLong(-delta, delta + 1);
        return Duration.ofMillis(baseMillis + offset);
    }

    /**
     * 创建延迟双删调度器（单线程、守护线程）
     *
     * @return 调度器实例
     */
    private static ScheduledExecutorService createScheduler() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, EVICT_THREAD_NAME);
            thread.setDaemon(true);
            return thread;
        });
    }
}
