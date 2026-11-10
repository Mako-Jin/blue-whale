package com.bluewhale.video.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.video.domain.model.CollectionQuery;
import com.bluewhale.video.domain.model.MovieCollection;
import com.bluewhale.video.domain.model.PublishState;
import com.bluewhale.video.domain.repository.MovieCollectionRepository;
import com.bluewhale.video.infrastructure.persistence.entity.MovieCollectionEntity;
import com.bluewhale.video.infrastructure.persistence.entity.MovieCollectionItemEntity;
import com.bluewhale.video.infrastructure.persistence.mapper.MovieCollectionItemMapper;
import com.bluewhale.video.infrastructure.persistence.mapper.MovieCollectionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 电影片单仓储实现（MyBatis-Plus，含片单条目关系）。
 *
 * <p>片单按运营排序升序、同序按 ID 倒序；删除片单为逻辑删除并同步物理删除条目，
 * 条目唯一性由数据库唯一键与调用方幂等判断双保险。</p>
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class MovieCollectionRepositoryImpl implements MovieCollectionRepository {

    private final MovieCollectionMapper collectionMapper;

    private final MovieCollectionItemMapper collectionItemMapper;

    @Override
    public Optional<MovieCollection> findById(Long id) {
        return Optional.ofNullable(collectionMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public List<MovieCollection> page(CollectionQuery query) {
        Page<MovieCollectionEntity> page = collectionMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L)), filter(query)
                        .orderByAsc(MovieCollectionEntity::getSortNo)
                        .orderByDesc(MovieCollectionEntity::getId));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(CollectionQuery query) {
        return collectionMapper.selectCount(filter(query));
    }

    @Override
    public void insert(MovieCollection collection) {
        MovieCollectionEntity entity = new MovieCollectionEntity();
        fill(entity, collection);
        collectionMapper.insert(entity);
        collection.setId(entity.getId());
        collection.setCreateTime(entity.getCreateTime());
        collection.setUpdateTime(entity.getUpdateTime());
    }

    @Override
    public void update(MovieCollection collection) {
        collectionMapper.update(null, new LambdaUpdateWrapper<MovieCollectionEntity>()
                .eq(MovieCollectionEntity::getId, collection.getId())
                .set(MovieCollectionEntity::getTitle, collection.getTitle())
                .set(MovieCollectionEntity::getDescription, collection.getDescription())
                .set(MovieCollectionEntity::getCoverFileId, collection.getCoverFileId())
                .set(MovieCollectionEntity::getSortNo,
                        collection.getSortNo() == null ? 0 : collection.getSortNo())
                .set(MovieCollectionEntity::getPublishStatus, nameOf(collection.getPublishState()))
                .set(MovieCollectionEntity::getItemCount,
                        collection.getItemCount() == null ? 0 : collection.getItemCount())
                .set(MovieCollectionEntity::getPublishedAt, collection.getPublishedAt()));
    }

    @Override
    public void delete(Long id) {
        collectionMapper.deleteById(id);
        collectionItemMapper.delete(new LambdaQueryWrapper<MovieCollectionItemEntity>()
                .eq(MovieCollectionItemEntity::getCollectionId, id));
    }

    @Override
    public List<Long> listItemContentIds(Long collectionId) {
        return collectionItemMapper.selectList(new LambdaQueryWrapper<MovieCollectionItemEntity>()
                        .eq(MovieCollectionItemEntity::getCollectionId, collectionId)
                        .orderByAsc(MovieCollectionItemEntity::getSortNo)
                        .orderByAsc(MovieCollectionItemEntity::getId))
                .stream().map(MovieCollectionItemEntity::getContentId).toList();
    }

    @Override
    public int countItems(Long collectionId) {
        return Math.toIntExact(collectionItemMapper.selectCount(
                new LambdaQueryWrapper<MovieCollectionItemEntity>()
                        .eq(MovieCollectionItemEntity::getCollectionId, collectionId)));
    }

    @Override
    public boolean existsItem(Long collectionId, Long contentId) {
        return collectionItemMapper.selectCount(new LambdaQueryWrapper<MovieCollectionItemEntity>()
                .eq(MovieCollectionItemEntity::getCollectionId, collectionId)
                .eq(MovieCollectionItemEntity::getContentId, contentId)) > 0L;
    }

    @Override
    public void addItem(Long collectionId, Long contentId, int sortNo) {
        MovieCollectionItemEntity entity = new MovieCollectionItemEntity();
        entity.setCollectionId(collectionId);
        entity.setContentId(contentId);
        entity.setSortNo(sortNo);
        entity.setCreateTime(LocalDateTime.now());
        collectionItemMapper.insert(entity);
    }

    @Override
    public void removeItem(Long collectionId, Long contentId) {
        collectionItemMapper.delete(new LambdaQueryWrapper<MovieCollectionItemEntity>()
                .eq(MovieCollectionItemEntity::getCollectionId, collectionId)
                .eq(MovieCollectionItemEntity::getContentId, contentId));
    }

    @Override
    public List<MovieCollection> listPublishedByContentId(Long contentId, int limit) {
        if (contentId == null || contentId <= 0) {
            return Collections.emptyList();
        }
        List<Long> collectionIds = collectionItemMapper.selectList(
                        new LambdaQueryWrapper<MovieCollectionItemEntity>()
                                .eq(MovieCollectionItemEntity::getContentId, contentId))
                .stream().map(MovieCollectionItemEntity::getCollectionId).distinct().toList();
        if (collectionIds.isEmpty()) {
            return Collections.emptyList();
        }
        return collectionMapper.selectList(new LambdaQueryWrapper<MovieCollectionEntity>()
                        .in(MovieCollectionEntity::getId, collectionIds)
                        .eq(MovieCollectionEntity::getPublishStatus, PublishState.PUBLISHED.name())
                        .orderByAsc(MovieCollectionEntity::getSortNo)
                        .orderByDesc(MovieCollectionEntity::getId)
                        .last("LIMIT " + Math.max(limit, 1)))
                .stream().map(this::toDomain).toList();
    }

    /**
     * 构造查询条件。
     *
     * @param query 查询条件
     * @return 查询条件
     */
    private LambdaQueryWrapper<MovieCollectionEntity> filter(CollectionQuery query) {
        return new LambdaQueryWrapper<MovieCollectionEntity>()
                .like(StringUtils.hasText(query.getKeyword()), MovieCollectionEntity::getTitle, query.getKeyword())
                .eq(query.getPublishState() != null, MovieCollectionEntity::getPublishStatus,
                        nameOf(query.getPublishState()));
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 片单
     */
    private MovieCollection toDomain(MovieCollectionEntity entity) {
        MovieCollection collection = new MovieCollection();
        collection.setId(entity.getId());
        collection.setTitle(entity.getTitle());
        collection.setDescription(entity.getDescription());
        collection.setCoverFileId(entity.getCoverFileId());
        collection.setSortNo(entity.getSortNo() == null ? 0 : entity.getSortNo());
        collection.setPublishState(PublishState.parse(entity.getPublishStatus()));
        collection.setItemCount(entity.getItemCount() == null ? 0 : entity.getItemCount());
        collection.setPublishedAt(entity.getPublishedAt());
        collection.setCreateTime(entity.getCreateTime());
        collection.setUpdateTime(entity.getUpdateTime());
        return collection;
    }

    /**
     * 领域模型写入实体。
     *
     * @param entity 目标实体
     * @param collection 片单
     */
    private void fill(MovieCollectionEntity entity, MovieCollection collection) {
        entity.setTitle(collection.getTitle());
        entity.setDescription(collection.getDescription());
        entity.setCoverFileId(collection.getCoverFileId());
        entity.setSortNo(collection.getSortNo() == null ? 0 : collection.getSortNo());
        entity.setPublishStatus(collection.getPublishState() == null
                ? PublishState.DRAFT.name() : collection.getPublishState().name());
        entity.setItemCount(collection.getItemCount() == null ? 0 : collection.getItemCount());
        entity.setPublishedAt(collection.getPublishedAt());
    }

    /**
     * 取发布状态名称用于查询与写入。
     *
     * @param value 发布状态
     * @return 状态名称；为空返回 {@code null}
     */
    private String nameOf(PublishState value) {
        return value == null ? null : value.name();
    }
}
