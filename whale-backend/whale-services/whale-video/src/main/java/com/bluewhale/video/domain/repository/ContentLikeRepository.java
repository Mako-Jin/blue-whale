package com.bluewhale.video.domain.repository;

import java.util.List;

/**
 * 内容点赞仓储端口（唯一键保证幂等）。
 *
 * @author BlueWhale
 */
public interface ContentLikeRepository {

    /**
     * 幂等点赞。
     *
     * @param userId 用户 ID
     * @param contentId 内容 ID
     * @return 本次新增返回 {@code true}，已点赞返回 {@code false}
     */
    boolean add(Long userId, Long contentId);

    /**
     * 取消点赞。
     *
     * @param userId 用户 ID
     * @param contentId 内容 ID
     * @return 本次删除返回 {@code true}
     */
    boolean remove(Long userId, Long contentId);

    /**
     * 判断是否已点赞。
     *
     * @param userId 用户 ID
     * @param contentId 内容 ID
     * @return 已点赞返回 {@code true}
     */
    boolean exists(Long userId, Long contentId);

    /**
     * 分页查询点赞的内容 ID（按点赞时间倒序）。
     *
     * @param userId 用户 ID
     * @param offset 偏移
     * @param limit 条数
     * @return 内容 ID 列表
     */
    List<Long> pageContentIds(Long userId, long offset, long limit);

    /**
     * 统计用户点赞总数。
     *
     * @param userId 用户 ID
     * @return 总数
     */
    long countByUser(Long userId);
}
