package com.bluewhale.video.infrastructure.redis;

import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.video.config.VideoProperties;
import com.bluewhale.video.domain.repository.PlayableCacheRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

/**
 * 可播状态缓存 Redis 实现。
 *
 * <p>key 约定 {@code whale:video:playable:{tenantId}:{contentId}}，值为
 * {@code 1}（可播）/ {@code 0}（不可播），TTL 默认 30 秒。按租户隔离，
 * 避免跨租户可见性差异导致误判；下架 / 版权下架 / 授权到期时主动删除。</p>
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class RedisPlayableCacheRepository implements PlayableCacheRepository {

    /** 可播状态 key 前缀 */
    private static final String PLAYABLE_KEY_PREFIX = "whale:video:playable:";

    /** 可播标识 */
    private static final String PLAYABLE = "1";

    /** 不可播标识 */
    private static final String NOT_PLAYABLE = "0";

    private final StringRedisTemplate stringRedisTemplate;

    private final VideoProperties videoProperties;

    @Override
    public Boolean find(Long contentId) {
        if (contentId == null || contentId <= 0) {
            return null;
        }
        String value = stringRedisTemplate.opsForValue().get(key(contentId));
        if (PLAYABLE.equals(value)) {
            return Boolean.TRUE;
        }
        if (NOT_PLAYABLE.equals(value)) {
            return Boolean.FALSE;
        }
        return null;
    }

    @Override
    public void save(Long contentId, boolean playable) {
        if (contentId == null || contentId <= 0) {
            return;
        }
        stringRedisTemplate.opsForValue().set(key(contentId), playable ? PLAYABLE : NOT_PLAYABLE,
                Duration.ofSeconds(Math.max(videoProperties.getPlayableCacheSeconds(), 1)));
    }

    @Override
    public void evict(Long contentId) {
        if (contentId != null && contentId > 0) {
            stringRedisTemplate.delete(key(contentId));
        }
    }

    /**
     * 拼装可播状态缓存 key（按租户隔离）。
     *
     * @param contentId 内容 ID
     * @return Redis key
     */
    private String key(Long contentId) {
        return PLAYABLE_KEY_PREFIX + TenantContext.segment() + ":" + contentId;
    }
}
