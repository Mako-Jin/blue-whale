package com.bluewhale.music.domain.repository;

import com.bluewhale.music.domain.model.Artist;

import java.util.List;
import java.util.Optional;

/**
 * 歌手仓储端口。
 *
 * @author BlueWhale
 */
public interface ArtistRepository {

    /**
     * 按主键查询歌手。
     *
     * @param id 歌手 ID
     * @return 歌手；不存在返回空
     */
    Optional<Artist> findById(Long id);

    /**
     * 判断租户内歌手名称是否已被占用。
     *
     * @param name 歌手名称
     * @param excludeId 排除的歌手 ID（更新时传当前 ID），新建传 {@code null}
     * @return 已占用返回 {@code true}
     */
    boolean existsByName(String name, Long excludeId);

    /**
     * 新增歌手。
     *
     * @param artist 歌手
     */
    void insert(Artist artist);

    /**
     * 更新歌手。
     *
     * @param artist 歌手
     */
    void update(Artist artist);

    /**
     * 逻辑删除歌手。
     *
     * @param id 歌手 ID
     */
    void delete(Long id);

    /**
     * 分页查询歌手。
     *
     * @param keyword 名称关键词，可为空
     * @param status 状态，可为空
     * @param current 页码
     * @param size 每页条数
     * @return 当前页歌手
     */
    List<Artist> page(String keyword, Integer status, long current, long size);

    /**
     * 统计符合条件的歌手总数。
     *
     * @param keyword 名称关键词，可为空
     * @param status 状态，可为空
     * @return 总数
     */
    long count(String keyword, Integer status);
}
