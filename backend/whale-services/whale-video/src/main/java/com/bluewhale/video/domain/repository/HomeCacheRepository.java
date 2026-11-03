package com.bluewhale.video.domain.repository;

import java.time.Duration;
import java.util.Optional;

/**
 * 首页 / 发现聚合缓存端口（形态为已序列化 JSON）。
 *
 * @author BlueWhale
 */
public interface HomeCacheRepository {

    /**
     * 读取缓存。
     *
     * @param tenantId 租户 ID
     * @param section 缓存分区（如 HOME / SEARCH:关键词）
     * @return 已序列化 JSON；未命中返回空
     */
    Optional<String> find(Long tenantId, String section);

    /**
     * 写入缓存。
     *
     * @param tenantId 租户 ID
     * @param section 缓存分区
     * @param payload 已序列化 JSON
     * @param ttl 有效期
     */
    void save(Long tenantId, String section, String payload, Duration ttl);

    /**
     * 失效某个分区。
     *
     * @param tenantId 租户 ID
     * @param section 缓存分区
     */
    void evict(Long tenantId, String section);
}
