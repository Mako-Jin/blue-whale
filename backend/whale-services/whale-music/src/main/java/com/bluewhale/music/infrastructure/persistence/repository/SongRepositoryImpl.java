package com.bluewhale.music.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.music.domain.model.CopyrightState;
import com.bluewhale.music.domain.model.PublishState;
import com.bluewhale.music.domain.model.Song;
import com.bluewhale.music.domain.model.SongQuery;
import com.bluewhale.music.domain.model.SongVisibility;
import com.bluewhale.music.domain.repository.SongRepository;
import com.bluewhale.music.infrastructure.persistence.entity.SongEntity;
import com.bluewhale.music.infrastructure.persistence.mapper.SongMapper;
import com.bluewhale.music.infrastructure.persistence.mapper.SongTagMapper;
import com.bluewhale.music.infrastructure.persistence.query.SongTagRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 歌曲仓储实现（MyBatis-Plus，含歌曲-标签关系）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class SongRepositoryImpl implements SongRepository {

    /** 热门排序标识 */
    private static final String SORT_HOT = "HOT";

    private final SongMapper songMapper;

    private final SongTagMapper songTagMapper;

    @Override
    public Optional<Song> findById(Long id) {
        return Optional.ofNullable(songMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public List<Song> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return songMapper.selectBatchIds(ids).stream().map(this::toDomain).toList();
    }

    @Override
    public void addLikeCount(Long songId, long delta) {
        songMapper.addLikeCount(songId, delta);
    }

    @Override
    public void insert(Song song) {
        SongEntity entity = new SongEntity();
        fill(entity, song);
        songMapper.insert(entity);
        song.setId(entity.getId());
        song.setCreateTime(entity.getCreateTime());
        song.setUpdateTime(entity.getUpdateTime());
    }

    @Override
    public void update(Song song) {
        SongEntity entity = new SongEntity();
        entity.setId(song.getId());
        fill(entity, song);
        songMapper.updateById(entity);
    }

    @Override
    public void delete(Long id) {
        songMapper.deleteById(id);
        songTagMapper.deleteBySongId(id);
    }

    @Override
    public List<Song> page(SongQuery query) {
        List<Long> tagSongIds = resolveTagSongIds(query);
        if (tagSongIds != null && tagSongIds.isEmpty()) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<SongEntity> wrapper = filter(query, tagSongIds);
        if (SORT_HOT.equals(query.getSortBy())) {
            wrapper.orderByDesc(SongEntity::getPlayCount).orderByDesc(SongEntity::getId);
        } else {
            wrapper.orderByDesc(SongEntity::getPublishedAt).orderByDesc(SongEntity::getId);
        }
        Page<SongEntity> page = songMapper.selectPage(new Page<>(query.getCurrent(), query.getSize()), wrapper);
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(SongQuery query) {
        List<Long> tagSongIds = resolveTagSongIds(query);
        if (tagSongIds != null && tagSongIds.isEmpty()) {
            return 0L;
        }
        return songMapper.selectCount(filter(query, tagSongIds));
    }

    @Override
    public void replaceTags(Long songId, Collection<Long> tagIds) {
        songTagMapper.deleteBySongId(songId);
        if (tagIds == null) {
            return;
        }
        for (Long tagId : tagIds) {
            if (tagId != null && tagId > 0) {
                songTagMapper.insertOne(songId, tagId);
            }
        }
    }

    @Override
    public List<Long> findTagIds(Long songId) {
        return songTagMapper.selectTagIdsBySongId(songId);
    }

    @Override
    public void removeTag(Long tagId) {
        songTagMapper.deleteByTagId(tagId);
    }

    @Override
    public Map<Long, List<Long>> findTagIdsBySongIds(Collection<Long> songIds) {
        if (songIds == null || songIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<SongTagRow> rows = songTagMapper.selectRowsBySongIds(songIds);
        Map<Long, List<Long>> result = new HashMap<>();
        for (SongTagRow row : rows) {
            result.computeIfAbsent(row.getSongId(), key -> new ArrayList<>()).add(row.getTagId());
        }
        return result;
    }

    /**
     * 解析标签筛选对应的歌曲 ID 集合。
     *
     * @param query 查询条件
     * @return 未指定标签返回 {@code null}；指定标签返回歌曲 ID 集合（可能为空）
     */
    private List<Long> resolveTagSongIds(SongQuery query) {
        if (query.getTagId() == null || query.getTagId() <= 0) {
            return null;
        }
        return songTagMapper.selectSongIdsByTagId(query.getTagId());
    }

    /**
     * 构造查询条件。
     *
     * @param query 查询条件
     * @param tagSongIds 标签筛选的歌曲 ID 集合，{@code null} 表示不筛选
     * @return 查询条件
     */
    private LambdaQueryWrapper<SongEntity> filter(SongQuery query, List<Long> tagSongIds) {
        LambdaQueryWrapper<SongEntity> wrapper = new LambdaQueryWrapper<SongEntity>()
                .eq(query.getArtistId() != null, SongEntity::getArtistId, query.getArtistId())
                .eq(query.getAlbumId() != null, SongEntity::getAlbumId, query.getAlbumId())
                .eq(query.getUploaderId() != null, SongEntity::getUploaderId, query.getUploaderId())
                .eq(query.getPublishState() != null, SongEntity::getPublishStatus, nameOf(query.getPublishState()))
                .eq(query.getVisibility() != null, SongEntity::getVisibility, nameOf(query.getVisibility()));
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(nested -> nested.like(SongEntity::getTitle, query.getKeyword())
                    .or().like(SongEntity::getSubtitle, query.getKeyword()));
        }
        if (tagSongIds != null) {
            wrapper.in(SongEntity::getId, tagSongIds);
        }
        return wrapper;
    }

    /**
     * 将实体转换为领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private Song toDomain(SongEntity entity) {
        Song song = new Song();
        song.setId(entity.getId());
        song.setUploaderId(entity.getUploaderId());
        song.setVisibility(parseVisibility(entity.getVisibility()));
        song.setAlbumId(entity.getAlbumId() == null ? 0L : entity.getAlbumId());
        song.setArtistId(entity.getArtistId());
        song.setTitle(entity.getTitle());
        song.setSubtitle(entity.getSubtitle());
        song.setAudioFileId(entity.getAudioFileId());
        song.setCoverFileId(entity.getCoverFileId());
        song.setDurationMs(entity.getDurationMs() == null ? 0L : entity.getDurationMs());
        song.setLyricFileId(entity.getLyricFileId());
        song.setLyricText(entity.getLyricText());
        song.setLanguage(entity.getLanguage());
        song.setPublishState(parsePublishState(entity.getPublishStatus()));
        song.setCopyrightState(parseCopyrightState(entity.getCopyrightStatus()));
        song.setPlayCount(entity.getPlayCount());
        song.setLikeCount(entity.getLikeCount());
        song.setCommentCount(entity.getCommentCount());
        song.setSortWeight(entity.getSortWeight());
        song.setPublishedAt(entity.getPublishedAt());
        song.setTakedownReason(entity.getTakedownReason());
        song.setCreateTime(entity.getCreateTime());
        song.setUpdateTime(entity.getUpdateTime());
        return song;
    }

    /**
     * 将领域模型字段写入实体。
     *
     * @param entity 目标实体
     * @param song 领域模型
     */
    private void fill(SongEntity entity, Song song) {
        entity.setUploaderId(song.getUploaderId());
        entity.setVisibility(song.getVisibility() == null ? SongVisibility.PUBLIC.name() : song.getVisibility().name());
        entity.setAlbumId(song.getAlbumId() == null ? 0L : song.getAlbumId());
        entity.setArtistId(song.getArtistId());
        entity.setTitle(song.getTitle());
        entity.setSubtitle(song.getSubtitle());
        entity.setAudioFileId(song.getAudioFileId());
        entity.setCoverFileId(song.getCoverFileId());
        entity.setDurationMs(song.getDurationMs() == null ? 0L : song.getDurationMs());
        entity.setLyricFileId(song.getLyricFileId());
        entity.setLyricText(song.getLyricText());
        entity.setLanguage(song.getLanguage());
        entity.setPublishStatus(parseName(song.getPublishState(), PublishState.DRAFT.name()));
        entity.setCopyrightStatus(parseName(song.getCopyrightState(), CopyrightState.DECLARED.name()));
        entity.setPlayCount(song.getPlayCount());
        entity.setLikeCount(song.getLikeCount());
        entity.setCommentCount(song.getCommentCount());
        entity.setSortWeight(song.getSortWeight());
        entity.setPublishedAt(song.getPublishedAt());
        entity.setTakedownReason(song.getTakedownReason());
    }

    /**
     * 取枚举名称用于查询条件。
     *
     * @param value 枚举值
     * @return 枚举名称；为空返回 {@code null}
     */
    private String nameOf(Enum<?> value) {
        return value == null ? null : value.name();
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
     * 解析可见性。
     *
     * @param value 存储值
     * @return 可见性；未知值回落 PUBLIC
     */
    private SongVisibility parseVisibility(String value) {
        for (SongVisibility item : SongVisibility.values()) {
            if (item.name().equals(value)) {
                return item;
            }
        }
        return SongVisibility.PUBLIC;
    }

    /**
     * 解析发布状态。
     *
     * @param value 存储值
     * @return 发布状态；未知值回落 DRAFT
     */
    private PublishState parsePublishState(String value) {
        for (PublishState item : PublishState.values()) {
            if (item.name().equals(value)) {
                return item;
            }
        }
        return PublishState.DRAFT;
    }

    /**
     * 解析版权状态。
     *
     * @param value 存储值
     * @return 版权状态；未知值回落 DECLARED
     */
    private CopyrightState parseCopyrightState(String value) {
        for (CopyrightState item : CopyrightState.values()) {
            if (item.name().equals(value)) {
                return item;
            }
        }
        return CopyrightState.DECLARED;
    }
}
