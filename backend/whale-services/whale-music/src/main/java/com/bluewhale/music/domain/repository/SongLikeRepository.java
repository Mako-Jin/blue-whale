package com.bluewhale.music.domain.repository;

import java.util.List;

/**
 * 歌曲收藏仓储端口。
 *
 * @author BlueWhale
 */
public interface SongLikeRepository {

    /**
     * 新增收藏；已存在时幂等返回 {@code false}。
     *
     * @param userId 用户 ID
     * @param songId 歌曲 ID
     * @return 实际新增返回 {@code true}
     */
    boolean add(Long userId, Long songId);

    /**
     * 取消收藏；不存在时幂等返回 {@code false}。
     *
     * @param userId 用户 ID
     * @param songId 歌曲 ID
     * @return 实际删除返回 {@code true}
     */
    boolean remove(Long userId, Long songId);

    /**
     * 判断是否已收藏。
     *
     * @param userId 用户 ID
     * @param songId 歌曲 ID
     * @return 已收藏返回 {@code true}
     */
    boolean exists(Long userId, Long songId);

    /**
     * 分页查询收藏歌曲 ID（按收藏时间倒序）。
     *
     * @param userId 用户 ID
     * @param offset 偏移量
     * @param limit 条数
     * @return 歌曲 ID 列表
     */
    List<Long> pageSongIds(Long userId, long offset, long limit);

    /**
     * 统计收藏总数。
     *
     * @param userId 用户 ID
     * @return 总数
     */
    long countByUser(Long userId);
}
