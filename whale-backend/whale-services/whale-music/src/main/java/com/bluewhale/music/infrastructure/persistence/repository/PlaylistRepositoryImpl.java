package com.bluewhale.music.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.music.domain.model.Playlist;
import com.bluewhale.music.domain.model.PlaylistQuery;
import com.bluewhale.music.domain.model.PlaylistSong;
import com.bluewhale.music.domain.model.PlaylistType;
import com.bluewhale.music.domain.model.PlaylistVisibility;
import com.bluewhale.music.domain.repository.PlaylistRepository;
import com.bluewhale.music.infrastructure.persistence.entity.PlaylistEntity;
import com.bluewhale.music.infrastructure.persistence.entity.PlaylistSongEntity;
import com.bluewhale.music.infrastructure.persistence.mapper.PlaylistMapper;
import com.bluewhale.music.infrastructure.persistence.mapper.PlaylistSongMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 歌单仓储实现（MyBatis-Plus，含歌单歌曲关系）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class PlaylistRepositoryImpl implements PlaylistRepository {

    /** 启用状态 */
    private static final int STATUS_ENABLED = 1;

    private final PlaylistMapper playlistMapper;

    private final PlaylistSongMapper playlistSongMapper;

    @Override
    public Optional<Playlist> findById(Long id) {
        return Optional.ofNullable(playlistMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public void insert(Playlist playlist) {
        PlaylistEntity entity = new PlaylistEntity();
        fill(entity, playlist);
        playlistMapper.insert(entity);
        playlist.setId(entity.getId());
        playlist.setCreateTime(entity.getCreateTime());
        playlist.setUpdateTime(entity.getUpdateTime());
    }

    @Override
    public void update(Playlist playlist) {
        PlaylistEntity entity = new PlaylistEntity();
        entity.setId(playlist.getId());
        fill(entity, playlist);
        playlistMapper.updateById(entity);
    }

    @Override
    public void delete(Long id) {
        playlistMapper.deleteById(id);
        playlistSongMapper.delete(new LambdaQueryWrapper<PlaylistSongEntity>()
                .eq(PlaylistSongEntity::getPlaylistId, id));
    }

    @Override
    public List<PlaylistSong> findSongs(Long playlistId) {
        return playlistSongMapper.selectList(new LambdaQueryWrapper<PlaylistSongEntity>()
                        .eq(PlaylistSongEntity::getPlaylistId, playlistId)
                        .orderByAsc(PlaylistSongEntity::getSortNo)
                        .orderByAsc(PlaylistSongEntity::getId))
                .stream().map(this::toDomainSong).toList();
    }

    @Override
    public void insertSong(PlaylistSong song) {
        PlaylistSongEntity entity = new PlaylistSongEntity();
        entity.setPlaylistId(song.getPlaylistId());
        entity.setSongId(song.getSongId());
        entity.setSortNo(song.getSortNo());
        entity.setAddedBy(song.getAddedBy());
        playlistSongMapper.insert(entity);
        song.setId(entity.getId());
        song.setCreateTime(entity.getCreateTime());
    }

    @Override
    public void deleteSong(Long playlistId, Long songId) {
        playlistSongMapper.delete(new LambdaQueryWrapper<PlaylistSongEntity>()
                .eq(PlaylistSongEntity::getPlaylistId, playlistId)
                .eq(PlaylistSongEntity::getSongId, songId));
    }

    @Override
    public void updateSongSort(Long playlistId, Long songId, int sortNo) {
        playlistSongMapper.update(null, new LambdaUpdateWrapper<PlaylistSongEntity>()
                .eq(PlaylistSongEntity::getPlaylistId, playlistId)
                .eq(PlaylistSongEntity::getSongId, songId)
                .set(PlaylistSongEntity::getSortNo, sortNo));
    }

    @Override
    public List<Playlist> page(PlaylistQuery query) {
        Page<PlaylistEntity> page = playlistMapper.selectPage(
                new Page<>(query.getCurrent(), query.getSize()), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(PlaylistQuery query) {
        return playlistMapper.selectCount(filter(query));
    }

    /**
     * 构造歌单查询条件（仅启用、可见性、类型与关键词）。
     *
     * @param query 查询条件
     * @return 查询条件
     */
    private LambdaQueryWrapper<PlaylistEntity> filter(PlaylistQuery query) {
        LambdaQueryWrapper<PlaylistEntity> wrapper = new LambdaQueryWrapper<PlaylistEntity>()
                .eq(PlaylistEntity::getStatus, STATUS_ENABLED);
        if (query.getOwnerId() != null) {
            wrapper.eq(PlaylistEntity::getOwnerId, query.getOwnerId());
        }
        if (query.getPlaylistType() != null) {
            wrapper.eq(PlaylistEntity::getPlaylistType, query.getPlaylistType().name());
        }
        if (query.isPublicOnly()) {
            wrapper.eq(PlaylistEntity::getVisibility, PlaylistVisibility.PUBLIC.name());
        } else if (query.getViewerId() != null) {
            wrapper.and(nested -> nested.eq(PlaylistEntity::getVisibility, PlaylistVisibility.PUBLIC.name())
                    .or().eq(PlaylistEntity::getOwnerId, query.getViewerId()));
        }
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(nested -> nested.like(PlaylistEntity::getTitle, query.getKeyword())
                    .or().like(PlaylistEntity::getDescription, query.getKeyword()));
        }
        wrapper.orderByDesc(PlaylistEntity::getCreateTime).orderByDesc(PlaylistEntity::getId);
        return wrapper;
    }

    /**
     * 将实体转换为领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private Playlist toDomain(PlaylistEntity entity) {
        Playlist playlist = new Playlist();
        playlist.setId(entity.getId());
        playlist.setOwnerId(entity.getOwnerId());
        playlist.setTitle(entity.getTitle());
        playlist.setCoverFileId(entity.getCoverFileId());
        playlist.setDescription(entity.getDescription());
        playlist.setPlaylistType(parseType(entity.getPlaylistType()));
        playlist.setVisibility(parseVisibility(entity.getVisibility()));
        playlist.setSongCount(entity.getSongCount());
        playlist.setPlayCount(entity.getPlayCount());
        playlist.setStatus(entity.getStatus());
        playlist.setCreateTime(entity.getCreateTime());
        playlist.setUpdateTime(entity.getUpdateTime());
        return playlist;
    }

    /**
     * 将歌单歌曲实体转换为领域模型。
     *
     * @param entity 实体
     * @return 歌单歌曲
     */
    private PlaylistSong toDomainSong(PlaylistSongEntity entity) {
        PlaylistSong song = new PlaylistSong();
        song.setId(entity.getId());
        song.setPlaylistId(entity.getPlaylistId());
        song.setSongId(entity.getSongId());
        song.setSortNo(entity.getSortNo());
        song.setAddedBy(entity.getAddedBy());
        song.setCreateTime(entity.getCreateTime());
        return song;
    }

    /**
     * 将领域模型字段写入歌单实体。
     *
     * @param entity 目标实体
     * @param playlist 领域模型
     */
    private void fill(PlaylistEntity entity, Playlist playlist) {
        entity.setOwnerId(playlist.getOwnerId());
        entity.setTitle(playlist.getTitle());
        entity.setCoverFileId(playlist.getCoverFileId());
        entity.setDescription(playlist.getDescription());
        entity.setPlaylistType(parseName(playlist.getPlaylistType(), PlaylistType.USER.name()));
        entity.setVisibility(parseName(playlist.getVisibility(), PlaylistVisibility.PUBLIC.name()));
        entity.setSongCount(playlist.getSongCount() == null ? 0 : playlist.getSongCount());
        entity.setPlayCount(playlist.getPlayCount() == null ? 0L : playlist.getPlayCount());
        entity.setStatus(playlist.getStatus() == null ? STATUS_ENABLED : playlist.getStatus());
    }

    /**
     * 取枚举名称用于写入。
     *
     * @param value 枚举值
     * @param fallback 兜底值
     * @return 枚举名称或兜底值
     */
    private String parseName(Enum<?> value, String fallback) {
        return value == null ? fallback : value.name();
    }

    /**
     * 解析歌单类型。
     *
     * @param value 存储值
     * @return 歌单类型；未知值回落 USER
     */
    private PlaylistType parseType(String value) {
        for (PlaylistType item : PlaylistType.values()) {
            if (item.name().equals(value)) {
                return item;
            }
        }
        return PlaylistType.USER;
    }

    /**
     * 解析歌单可见性。
     *
     * @param value 存储值
     * @return 可见性；未知值回落 PUBLIC
     */
    private PlaylistVisibility parseVisibility(String value) {
        for (PlaylistVisibility item : PlaylistVisibility.values()) {
            if (item.name().equals(value)) {
                return item;
            }
        }
        return PlaylistVisibility.PUBLIC;
    }
}
