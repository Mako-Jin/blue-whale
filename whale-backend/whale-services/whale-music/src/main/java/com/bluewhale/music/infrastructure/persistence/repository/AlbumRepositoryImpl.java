package com.bluewhale.music.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.music.domain.model.Album;
import com.bluewhale.music.domain.model.PublishState;
import com.bluewhale.music.domain.repository.AlbumRepository;
import com.bluewhale.music.infrastructure.persistence.entity.AlbumEntity;
import com.bluewhale.music.infrastructure.persistence.mapper.AlbumMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 专辑仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class AlbumRepositoryImpl implements AlbumRepository {

    private final AlbumMapper albumMapper;

    @Override
    public Optional<Album> findById(Long id) {
        return Optional.ofNullable(albumMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public void insert(Album album) {
        AlbumEntity entity = new AlbumEntity();
        fill(entity, album);
        albumMapper.insert(entity);
        album.setId(entity.getId());
        album.setCreateTime(entity.getCreateTime());
        album.setUpdateTime(entity.getUpdateTime());
    }

    @Override
    public void update(Album album) {
        AlbumEntity entity = new AlbumEntity();
        entity.setId(album.getId());
        fill(entity, album);
        albumMapper.updateById(entity);
    }

    @Override
    public void delete(Long id) {
        albumMapper.deleteById(id);
    }

    @Override
    public List<Album> page(String keyword, Long artistId, PublishState publishState, long current, long size) {
        LambdaQueryWrapper<AlbumEntity> wrapper = filter(keyword, artistId, publishState);
        wrapper.orderByAsc(AlbumEntity::getSortNo).orderByDesc(AlbumEntity::getId);
        Page<AlbumEntity> page = albumMapper.selectPage(new Page<>(current, size), wrapper);
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(String keyword, Long artistId, PublishState publishState) {
        return albumMapper.selectCount(filter(keyword, artistId, publishState));
    }

    @Override
    public long countByArtist(Long artistId) {
        return albumMapper.selectCount(new LambdaQueryWrapper<AlbumEntity>()
                .eq(AlbumEntity::getArtistId, artistId));
    }

    /**
     * 构造查询条件。
     *
     * @param keyword 标题关键词，可为空
     * @param artistId 歌手 ID，可为空
     * @param publishState 发布状态，可为空
     * @return 查询条件
     */
    private LambdaQueryWrapper<AlbumEntity> filter(String keyword, Long artistId, PublishState publishState) {
        return new LambdaQueryWrapper<AlbumEntity>()
                .like(StringUtils.hasText(keyword), AlbumEntity::getTitle, keyword)
                .eq(artistId != null, AlbumEntity::getArtistId, artistId)
                .eq(publishState != null, AlbumEntity::getPublishStatus, publishStateName(publishState));
    }

    /**
     * 将实体转换为领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private Album toDomain(AlbumEntity entity) {
        Album album = new Album();
        album.setId(entity.getId());
        album.setArtistId(entity.getArtistId());
        album.setTitle(entity.getTitle());
        album.setCoverFileId(entity.getCoverFileId());
        album.setReleaseDate(entity.getReleaseDate());
        album.setAlbumType(entity.getAlbumType());
        album.setIntro(entity.getIntro());
        album.setPublishState(parsePublishState(entity.getPublishStatus()));
        album.setSortNo(entity.getSortNo());
        album.setCreateTime(entity.getCreateTime());
        album.setUpdateTime(entity.getUpdateTime());
        return album;
    }

    /**
     * 将领域模型字段写入实体。
     *
     * @param entity 目标实体
     * @param album 领域模型
     */
    private void fill(AlbumEntity entity, Album album) {
        entity.setArtistId(album.getArtistId());
        entity.setTitle(album.getTitle());
        entity.setCoverFileId(album.getCoverFileId());
        entity.setReleaseDate(album.getReleaseDate());
        entity.setAlbumType(album.getAlbumType());
        entity.setIntro(album.getIntro());
        entity.setPublishStatus(publishStateName(album.getPublishState()));
        entity.setSortNo(album.getSortNo());
    }

    /**
     * 将发布状态转换为数据库存储值。
     *
     * @param state 发布状态
     * @return 存储值；为空时默认 DRAFT
     */
    private String publishStateName(PublishState state) {
        return state == null ? PublishState.DRAFT.name() : state.name();
    }

    /**
     * 解析数据库发布状态。
     *
     * @param value 存储值
     * @return 发布状态；未知值回落 DRAFT
     */
    private PublishState parsePublishState(String value) {
        if (!StringUtils.hasText(value)) {
            return PublishState.DRAFT;
        }
        for (PublishState state : PublishState.values()) {
            if (state.name().equals(value)) {
                return state;
            }
        }
        return PublishState.DRAFT;
    }
}
