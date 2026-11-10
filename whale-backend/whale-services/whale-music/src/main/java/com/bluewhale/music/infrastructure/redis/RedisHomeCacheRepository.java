package com.bluewhale.music.infrastructure.redis;

import com.bluewhale.music.domain.repository.HomeCacheRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

/**
 * 音乐首页聚合缓存 Redis 实现。
 *
 * <p>key 约定 {@code whale:music:home:{tenantId}}，缓存已序列化的 JSON，
 * TTL 由调用方传入（60 秒 + 0–10 秒随机抖动）。</p>
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class RedisHomeCacheRepository implements HomeCacheRepository {

    /** 首页缓存 key 前缀 */
    private static final String HOME_KEY_PREFIX = "whale:music:home:";

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public Optional<String> find(Long tenantId) {
        if (tenantId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(stringRedisTemplate.opsForValue().get(key(tenantId)));
    }

    @Override
    public void save(Long tenantId, String payload, Duration ttl) {
        if (tenantId == null || payload == null) {
            return;
        }
        stringRedisTemplate.opsForValue().set(key(tenantId), payload, ttl);
    }

    @Override
    public void evict(Long tenantId) {
        if (tenantId != null) {
            stringRedisTemplate.delete(key(tenantId));
        }
    }

    /**
     * 拼装首页缓存 key。
     *
     * @param tenantId 租户 ID
     * @return Redis key
     */
    private String key(Long tenantId) {
        return HOME_KEY_PREFIX + tenantId;
    }
}
