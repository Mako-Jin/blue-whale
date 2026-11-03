package com.bluewhale.video.domain.repository;

/**
 * 可播状态缓存端口（30 秒 TTL，下架 / 版权下架 / 授权到期主动删除）。
 *
 * @author BlueWhale
 */
public interface PlayableCacheRepository {

    /**
     * 读取可播状态。
     *
     * @param contentId 内容 ID
     * @return 缓存命中返回状态；未命中返回空
     */
    Boolean find(Long contentId);

    /**
     * 写入可播状态。
     *
     * @param contentId 内容 ID
     * @param playable 是否可播
     */
    void save(Long contentId, boolean playable);

    /**
     * 删除可播状态缓存。
     *
     * @param contentId 内容 ID
     */
    void evict(Long contentId);
}
