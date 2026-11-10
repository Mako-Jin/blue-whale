package com.bluewhale.music.domain.repository;

import com.bluewhale.music.domain.model.Playlist;
import com.bluewhale.music.domain.model.PlaylistQuery;
import com.bluewhale.music.domain.model.PlaylistSong;

import java.util.List;
import java.util.Optional;

/**
 * 歌单仓储端口（含歌单歌曲关系）。
 *
 * @author BlueWhale
 */
public interface PlaylistRepository {

    /**
     * 按主键查询歌单。
     *
     * @param id 歌单 ID
     * @return 歌单；不存在返回空
     */
    Optional<Playlist> findById(Long id);

    /**
     * 新增歌单。
     *
     * @param playlist 歌单
     */
    void insert(Playlist playlist);

    /**
     * 更新歌单主体（标题、简介、可见性、歌曲数量等）。
     *
     * @param playlist 歌单
     */
    void update(Playlist playlist);

    /**
     * 逻辑删除歌单，并物理清理其歌曲关系。
     *
     * @param id 歌单 ID
     */
    void delete(Long id);

    /**
     * 查询歌单内歌曲（按排序号升序）。
     *
     * @param playlistId 歌单 ID
     * @return 歌单歌曲列表
     */
    List<PlaylistSong> findSongs(Long playlistId);

    /**
     * 新增歌单歌曲关系。
     *
     * @param song 歌单歌曲
     */
    void insertSong(PlaylistSong song);

    /**
     * 删除歌单歌曲关系。
     *
     * @param playlistId 歌单 ID
     * @param songId 歌曲 ID
     */
    void deleteSong(Long playlistId, Long songId);

    /**
     * 更新歌单内单首歌曲的排序号。
     *
     * @param playlistId 歌单 ID
     * @param songId 歌曲 ID
     * @param sortNo 排序号
     */
    void updateSongSort(Long playlistId, Long songId, int sortNo);

    /**
     * 分页查询歌单。
     *
     * @param query 查询条件
     * @return 当前页歌单
     */
    List<Playlist> page(PlaylistQuery query);

    /**
     * 统计符合条件的歌单总数。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(PlaylistQuery query);
}
