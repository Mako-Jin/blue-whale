package com.bluewhale.music.domain.repository;

import com.bluewhale.music.domain.model.Song;
import com.bluewhale.music.domain.model.SongQuery;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 歌曲仓储端口（含歌曲-标签关系）。
 *
 * @author BlueWhale
 */
public interface SongRepository {

    /**
     * 按主键查询歌曲。
     *
     * @param id 歌曲 ID
     * @return 歌曲；不存在返回空
     */
    Optional<Song> findById(Long id);

    /**
     * 按主键批量查询歌曲。
     *
     * @param ids 歌曲 ID 集合
     * @return 歌曲列表；集合为空返回空列表
     */
    List<Song> findByIds(Collection<Long> ids);

    /**
     * 原子调整收藏数（+1 / -1，结果不小于 0）。
     *
     * @param songId 歌曲 ID
     * @param delta 增量
     */
    void addLikeCount(Long songId, long delta);

    /**
     * 新增歌曲。
     *
     * @param song 歌曲
     */
    void insert(Song song);

    /**
     * 更新歌曲。
     *
     * @param song 歌曲
     */
    void update(Song song);

    /**
     * 逻辑删除歌曲。
     *
     * @param id 歌曲 ID
     */
    void delete(Long id);

    /**
     * 分页查询歌曲。
     *
     * @param query 查询条件
     * @return 当前页歌曲
     */
    List<Song> page(SongQuery query);

    /**
     * 统计符合条件的歌曲总数。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(SongQuery query);

    /**
     * 覆盖式保存歌曲标签关系。
     *
     * @param songId 歌曲 ID
     * @param tagIds 标签 ID 集合；为空表示清空
     */
    void replaceTags(Long songId, Collection<Long> tagIds);

    /**
     * 查询歌曲的标签 ID。
     *
     * @param songId 歌曲 ID
     * @return 标签 ID 列表
     */
    List<Long> findTagIds(Long songId);

    /**
     * 删除某标签与全部歌曲的关系（标签删除时清理）。
     *
     * @param tagId 标签 ID
     */
    void removeTag(Long tagId);

    /**
     * 批量查询歌曲标签关系。
     *
     * @param songIds 歌曲 ID 集合
     * @return 歌曲 ID → 标签 ID 列表；集合为空返回空映射
     */
    Map<Long, List<Long>> findTagIdsBySongIds(Collection<Long> songIds);
}
