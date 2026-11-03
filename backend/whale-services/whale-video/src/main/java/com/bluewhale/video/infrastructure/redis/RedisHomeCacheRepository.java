package com.bluewhale.video.infrastructure.redis;

import com.bluewhale.video.domain.repository.HomeCacheRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

/**
 * 视频首页 / 发现聚合缓存 Redis 实现。
 *
 * <p>key 约定 {@code whale:video:home:{tenantId}:{section}}，缓存已序列化的 JSON，
 * TTL 由调用方传入（默认 60 秒 + 0–10 秒随机抖动）。</p>
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class RedisHomeCacheRepository implements HomeCacheRepository {

    /** 首页缓存 key 前缀 */
    private static final String HOME_KEY_PREFIX = "whale:video:home:";

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
    public void evict(Long tenantId, String section) {
        if (tenantId != null && section != null && !section.isBlank()) {
            stringRedisTemplate.delete(key(tenantId, section));
        }
    }

    /**
     * 拼装聚合缓存 key。
     *
     * @param tenantId 租户 ID
     * @param section 缓存分区
     * @return Redis key
     */
    private String key(Long tenantId, String section) {
        return HOME_KEY_PREFIX + tenantId + ":" + section;
    }
}
