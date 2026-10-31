package com.bluewhale.music.domain.repository;

import java.time.Duration;
import java.util.Optional;

/**
 * 音乐首页聚合缓存端口（缓存内容为已序列化的 JSON 文本）。
 *
 * @author BlueWhale
 */
public interface HomeCacheRepository {

    /**
     * 读取租户首页缓存。
     *
     * @param tenantId 租户 ID
     * @return 缓存 JSON；不存在返回空
     */
    Optional<String> find(Long tenantId);

    /**
     * 写入租户首页缓存。
     *
     * @param tenantId 租户 ID
     * @param payload 已序列化的 JSON 文本
     * @param ttl 过期时间
     */
    void save(Long tenantId, String payload, Duration ttl);

    /**
     * 删除租户首页缓存（歌曲或官方歌单变更后主动失效）。
     *
     * @param tenantId 租户 ID，可为 {@code null}（无租户上下文时忽略）
     */
    void evict(Long tenantId);
}
