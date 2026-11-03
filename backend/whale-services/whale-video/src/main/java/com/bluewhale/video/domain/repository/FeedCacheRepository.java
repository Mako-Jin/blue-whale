package com.bluewhale.video.domain.repository;

import java.time.Duration;
import java.util.Optional;

/**
 * feed 首屏缓存端口（形态为已序列化 JSON）。
 *
 * <p>只缓存首屏（游标为空），TTL 默认 30 秒；内容发布 / 下架 / 版权下架时按租户整体失效。</p>
 *
 * @author BlueWhale
 */
public interface FeedCacheRepository {

    /**
     * 读取首屏缓存。
     *
     * @param tenantId 租户 ID
     * @param section 缓存分区（类型 + 排序 + 分类）
     * @return 已序列化 JSON；未命中返回空
     */
    Optional<String> find(Long tenantId, String section);

    /**
     * 写入首屏缓存。
     *
     * @param tenantId 租户 ID
     * @param section 缓存分区
     * @param payload 已序列化 JSON
     * @param ttl 有效期
     */
    void save(Long tenantId, String section, String payload, Duration ttl);

    /**
     * 失效该租户下的全部分区。
     *
     * @param tenantId 租户 ID
     */
    void evictAll(Long tenantId);
}
