package com.bluewhale.music.domain.repository;

import com.bluewhale.music.domain.model.Album;
import com.bluewhale.music.domain.model.PublishState;

import java.util.List;
import java.util.Optional;

/**
 * 专辑仓储端口。
 *
 * @author BlueWhale
 */
public interface AlbumRepository {

    /**
     * 按主键查询专辑。
     *
     * @param id 专辑 ID
     * @return 专辑；不存在返回空
     */
    Optional<Album> findById(Long id);

    /**
     * 新增专辑。
     *
     * @param album 专辑
     */
    void insert(Album album);

    /**
     * 更新专辑。
     *
     * @param album 专辑
     */
    void update(Album album);

    /**
     * 逻辑删除专辑。
     *
     * @param id 专辑 ID
     */
    void delete(Long id);

    /**
     * 分页查询专辑。
     *
     * @param keyword 标题关键词，可为空
     * @param artistId 歌手 ID，可为空
     * @param publishState 发布状态，可为空
     * @param current 页码
     * @param size 每页条数
     * @return 当前页专辑
     */
    List<Album> page(String keyword, Long artistId, PublishState publishState, long current, long size);

    /**
     * 统计符合条件的专辑总数。
     *
     * @param keyword 标题关键词，可为空
     * @param artistId 歌手 ID，可为空
     * @param publishState 发布状态，可为空
     * @return 总数
     */
    long count(String keyword, Long artistId, PublishState publishState);

    /**
     * 统计歌手名下未删除的专辑数量。
     *
     * @param artistId 歌手 ID
     * @return 专辑数量
     */
    long countByArtist(Long artistId);
}
