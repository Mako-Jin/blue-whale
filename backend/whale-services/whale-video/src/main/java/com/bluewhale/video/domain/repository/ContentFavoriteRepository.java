package com.bluewhale.video.domain.repository;

import java.util.List;

/**
 * 内容收藏仓储端口（唯一键保证幂等）。
 *
 * @author BlueWhale
 */
public interface ContentFavoriteRepository {

    /**
     * 幂等收藏。
     *
     * @param userId 用户 ID
     * @param contentId 内容 ID
     * @return 本次新增返回 {@code true}，已收藏返回 {@code false}
     */
    boolean add(Long userId, Long contentId);

    /**
     * 取消收藏。
     *
     * @param userId 用户 ID
     * @param contentId 内容 ID
     * @return 本次删除返回 {@code true}
     */
    boolean remove(Long userId, Long contentId);

    /**
     * 判断是否已收藏。
     *
     * @param userId 用户 ID
     * @param contentId 内容 ID
     * @return 已收藏返回 {@code true}
     */
    boolean exists(Long userId, Long contentId);

    /**
     * 分页查询收藏的内容 ID（按收藏时间倒序）。
     *
     * @param userId 用户 ID
     * @param offset 偏移
     * @param limit 条数
     * @return 内容 ID 列表
     */
    List<Long> pageContentIds(Long userId, long offset, long limit);

    /**
     * 统计用户收藏总数。
     *
     * @param userId 用户 ID
     * @return 总数
     */
    long countByUser(Long userId);
}
