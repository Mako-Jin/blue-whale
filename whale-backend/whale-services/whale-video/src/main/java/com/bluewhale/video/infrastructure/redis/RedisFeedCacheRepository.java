package com.bluewhale.video.infrastructure.redis;

import com.bluewhale.video.domain.repository.FeedCacheRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * feed 首屏缓存 Redis 实现。
 *
 * <p>key 约定 {@code whale:video:cache:feed:{tenantId}:{section}}，section 由内容类型、
 * 排序与分类组成；失效使用 {@code SCAN} 按租户前缀删除，避免阻塞 Redis。</p>
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class RedisFeedCacheRepository implements FeedCacheRepository {

    /** feed 缓存 key 前缀 */
    private static final String FEED_KEY_PREFIX = "whale:video:cache:feed:";

    /** SCAN 单批建议条数 */
    private static final long SCAN_COUNT = 100L;

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public Optional<String> find(Long tenantId, String section) {
        if (tenantId == null || section == null || section.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(stringRedisTemplate.opsForValue().get(key(tenantId, section)));
    }

    @Override
    public void save(Long tenantId, String section, String payload, Duration ttl) {
        if (tenantId == null || section == null || section.isBlank() || payload == null) {
            return;
        }
        stringRedisTemplate.opsForValue().set(key(tenantId, section), payload, ttl);
    }

    @Override
    public void evictAll(Long tenantId) {
        if (tenantId == null) {
            return;
        }
        Set<String> keys = new HashSet<>();
        try (Cursor<String> cursor = stringRedisTemplate.scan(ScanOptions.scanOptions()
                .match(FEED_KEY_PREFIX + tenantId + ":*").count(SCAN_COUNT).build())) {
            cursor.forEachRemaining(keys::add);
        }
        if (!keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    /**
     * 拼装 feed 缓存 key。
     *
     * @param tenantId 租户 ID
     * @param section 缓存分区
     * @return Redis key
     */
    private String key(Long tenantId, String section) {
        return FEED_KEY_PREFIX + tenantId + ":" + section;
    }
}
