package com.bluewhale.music.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.music.domain.model.Artist;
import com.bluewhale.music.domain.repository.ArtistRepository;
import com.bluewhale.music.infrastructure.persistence.entity.ArtistEntity;
import com.bluewhale.music.infrastructure.persistence.mapper.ArtistMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 歌手仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class ArtistRepositoryImpl implements ArtistRepository {

    private final ArtistMapper artistMapper;

    @Override
    public Optional<Artist> findById(Long id) {
        return Optional.ofNullable(artistMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public boolean existsByName(String name, Long excludeId) {
        LambdaQueryWrapper<ArtistEntity> wrapper = new LambdaQueryWrapper<ArtistEntity>()
                .eq(ArtistEntity::getName, name);
        if (excludeId != null) {
            wrapper.ne(ArtistEntity::getId, excludeId);
        }
        return artistMapper.selectCount(wrapper) > 0;
    }

    @Override
    public void insert(Artist artist) {
        ArtistEntity entity = new ArtistEntity();
        fill(entity, artist);
        artistMapper.insert(entity);
        artist.setId(entity.getId());
        artist.setCreateTime(entity.getCreateTime());
        artist.setUpdateTime(entity.getUpdateTime());
    }

    @Override
    public void update(Artist artist) {
        ArtistEntity entity = new ArtistEntity();
        entity.setId(artist.getId());
        fill(entity, artist);
        artistMapper.updateById(entity);
    }

    @Override
    public void delete(Long id) {
        artistMapper.deleteById(id);
    }

    @Override
    public List<Artist> page(String keyword, Integer status, long current, long size) {
        LambdaQueryWrapper<ArtistEntity> wrapper = filter(keyword, status);
        wrapper.orderByDesc(ArtistEntity::getId);
        Page<ArtistEntity> page = artistMapper.selectPage(new Page<>(current, size), wrapper);
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(String keyword, Integer status) {
        return artistMapper.selectCount(filter(keyword, status));
    }

    /**
     * 构造查询条件。
     *
     * @param keyword 名称关键词，可为空
     * @param status 状态，可为空
     * @return 查询条件
     */
    private LambdaQueryWrapper<ArtistEntity> filter(String keyword, Integer status) {
        LambdaQueryWrapper<ArtistEntity> wrapper = new LambdaQueryWrapper<ArtistEntity>()
                .like(StringUtils.hasText(keyword), ArtistEntity::getName, keyword)
                .eq(status != null, ArtistEntity::getStatus, status);
        return wrapper;
    }

    /**
     * 将实体字段写入领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private Artist toDomain(ArtistEntity entity) {
        Artist artist = new Artist();
        artist.setId(entity.getId());
        artist.setName(entity.getName());
        artist.setAvatarFileId(entity.getAvatarFileId());
        artist.setRegion(entity.getRegion());
        artist.setBio(entity.getBio());
        artist.setStatus(entity.getStatus());
        artist.setCreateTime(entity.getCreateTime());
        artist.setUpdateTime(entity.getUpdateTime());
        return artist;
    }

    /**
     * 将领域模型字段写入实体。
     *
     * @param entity 目标实体
     * @param artist 领域模型
     */
    private void fill(ArtistEntity entity, Artist artist) {
        entity.setName(artist.getName());
        entity.setAvatarFileId(artist.getAvatarFileId());
        entity.setRegion(artist.getRegion());
        entity.setBio(artist.getBio());
        entity.setStatus(artist.getStatus());
    }
}
