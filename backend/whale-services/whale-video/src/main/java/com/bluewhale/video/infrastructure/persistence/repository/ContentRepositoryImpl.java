package com.bluewhale.video.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.video.domain.model.Content;
import com.bluewhale.video.domain.model.ContentQuery;
import com.bluewhale.video.domain.model.ContentType;
import com.bluewhale.video.domain.model.ContentVisibility;
import com.bluewhale.video.domain.model.CopyrightSource;
import com.bluewhale.video.domain.model.CopyrightState;
import com.bluewhale.video.domain.model.FeedCursor;
import com.bluewhale.video.domain.model.FeedQuery;
import com.bluewhale.video.domain.model.PublishState;
import com.bluewhale.video.domain.repository.ContentRepository;
import com.bluewhale.video.infrastructure.persistence.entity.ContentEntity;
import com.bluewhale.video.infrastructure.persistence.mapper.ContentMapper;
import com.bluewhale.video.infrastructure.persistence.mapper.ContentTagMapper;
import com.bluewhale.video.infrastructure.persistence.query.ContentTagRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 内容仓储实现（MyBatis-Plus，含内容-标签关系与原子计数）。
 *
 * <p>更新内容时不清空计数列，避免覆盖并发点赞 / 收藏 / 评论产生的增量；
 * 可空字段（版权到期、版权下架原因与时间）使用显式 {@code SET} 以便恢复时清空。</p>
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class ContentRepositoryImpl implements ContentRepository {

    /** 热门排序标识 */
    private static final String SORT_HOT = "HOT";

    /** feed 默认条数 */
    private static final long DEFAULT_FEED_LIMIT = 20L;

    /** feed 单次最大条数 */
    private static final long MAX_FEED_LIMIT = 200L;

    private final ContentMapper contentMapper;

    private final ContentTagMapper contentTagMapper;

    @Override
    public Optional<Content> findById(Long id) {
        return Optional.ofNullable(contentMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public List<Content> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return contentMapper.selectBatchIds(ids).stream().map(this::toDomain).toList();
    }

    @Override
    public void insert(Content content) {
        ContentEntity entity = new ContentEntity();
        fill(entity, content);
        contentMapper.insert(entity);
        content.setId(entity.getId());
        content.setCreateTime(entity.getCreateTime());
        content.setUpdateTime(entity.getUpdateTime());
    }

    @Override
    public void update(Content content) {
        ContentEntity entity = new ContentEntity();
        entity.setId(content.getId());
        fill(entity, content);
        contentMapper.update(entity, new LambdaUpdateWrapper<ContentEntity>()
                .eq(ContentEntity::getId, content.getId())
                .set(ContentEntity::getCopyrightExpireAt, content.getCopyrightExpireAt())
                .set(ContentEntity::getTakedownReason, content.getTakedownReason())
                .set(ContentEntity::getTakedownAt, content.getTakedownAt()));
    }

    @Override
    public void delete(Long id) {
        contentMapper.deleteById(id);
        contentTagMapper.deleteByContentId(id);
    }

    @Override
    public List<Content> page(ContentQuery query) {
        List<Long> tagContentIds = resolveTagContentIds(query);
        if (tagContentIds != null && tagContentIds.isEmpty()) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<ContentEntity> wrapper = filter(query, tagContentIds);
        if (SORT_HOT.equals(query.getSortBy())) {
            wrapper.orderByDesc(ContentEntity::getPlayCount)
                    .orderByDesc(ContentEntity::getLikeCount)
                    .orderByDesc(ContentEntity::getId);
        } else {
            wrapper.orderByDesc(ContentEntity::getPublishedAt).orderByDesc(ContentEntity::getId);
        }
        Page<ContentEntity> page = contentMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L)), wrapper);
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(ContentQuery query) {
        List<Long> tagContentIds = resolveTagContentIds(query);
        if (tagContentIds != null && tagContentIds.isEmpty()) {
            return 0L;
        }
        return contentMapper.selectCount(filter(query, tagContentIds));
    }

    @Override
    public List<Content> feed(FeedQuery query) {
        long limit = query.getLimit() <= 0 ? DEFAULT_FEED_LIMIT : Math.min(query.getLimit(), MAX_FEED_LIMIT);
        LambdaQueryWrapper<ContentEntity> wrapper = filter(feedFilter(query), null);
        boolean hot = SORT_HOT.equals(query.getSortBy());
        applyCursor(wrapper, query.getCursor(), hot);
        if (hot) {
            wrapper.orderByDesc(ContentEntity::getHotScore).orderByDesc(ContentEntity::getId);
        } else {
            wrapper.orderByDesc(ContentEntity::getPublishedAt).orderByDesc(ContentEntity::getId);
        }
        wrapper.last("LIMIT " + limit);
        return contentMapper.selectList(wrapper).stream().map(this::toDomain).toList();
    }

    @Override
    public void addLikeCount(Long contentId, long delta) {
        contentMapper.addLikeCount(contentId, delta);
    }

    @Override
    public void addFavoriteCount(Long contentId, long delta) {
        contentMapper.addFavoriteCount(contentId, delta);
    }

    @Override
    public void addCommentCount(Long contentId, long delta) {
        contentMapper.addCommentCount(contentId, delta);
    }

    @Override
    public void addPlayCount(Long contentId, long delta) {
        contentMapper.addPlayCount(contentId, delta);
    }

    @Override
    public void replaceTags(Long contentId, Collection<Long> tagIds) {
        contentTagMapper.deleteByContentId(contentId);
        if (tagIds == null) {
            return;
        }
        for (Long tagId : tagIds) {
            if (tagId != null && tagId > 0) {
                contentTagMapper.insertOne(contentId, tagId);
            }
        }
    }

    @Override
    public List<Long> findTagIds(Long contentId) {
        return contentTagMapper.selectTagIdsByContentId(contentId);
    }

    @Override
    public Map<Long, List<Long>> findTagIdsByContentIds(Collection<Long> contentIds) {
        if (contentIds == null || contentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<ContentTagRow> rows = contentTagMapper.selectRowsByContentIds(contentIds);
        Map<Long, List<Long>> result = new HashMap<>();
        for (ContentTagRow row : rows) {
            result.computeIfAbsent(row.getContentId(), key -> new ArrayList<>()).add(row.getTagId());
        }
        return result;
    }

    @Override
    public boolean existsSeriesEpisode(Long seriesId, Integer episodeNo, Long excludeContentId) {
        if (seriesId == null || seriesId <= 0 || episodeNo == null || episodeNo <= 0) {
            return false;
        }
        LambdaQueryWrapper<ContentEntity> wrapper = new LambdaQueryWrapper<ContentEntity>()
                .eq(ContentEntity::getSeriesId, seriesId)
                .eq(ContentEntity::getEpisodeNo, episodeNo)
                .ne(excludeContentId != null && excludeContentId > 0,
                        ContentEntity::getId, excludeContentId);
        return contentMapper.selectCount(wrapper) > 0;
    }

    @Override
    public int countPublishedEpisodes(Long seriesId) {
        if (seriesId == null || seriesId <= 0) {
            return 0;
        }
        return Math.toIntExact(contentMapper.selectCount(publishedEpisodeFilter(seriesId)));
    }

    @Override
    public List<Content> listPublishedEpisodes(Long seriesId) {
        if (seriesId == null || seriesId <= 0) {
            return Collections.emptyList();
        }
        return contentMapper.selectList(publishedEpisodeFilter(seriesId)
                        .orderByAsc(ContentEntity::getEpisodeNo)
                        .orderByAsc(ContentEntity::getId))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public long totalPlayCount() {
        return contentMapper.sumPlayCount();
    }

    /**
     * 系列内已发布且公开集数的查询条件。
     *
     * @param seriesId 系列 ID
     * @return 查询条件
     */
    private LambdaQueryWrapper<ContentEntity> publishedEpisodeFilter(Long seriesId) {
        return new LambdaQueryWrapper<ContentEntity>()
                .eq(ContentEntity::getSeriesId, seriesId)
                .eq(ContentEntity::getPublishState, PublishState.PUBLISHED.name())
                .eq(ContentEntity::getVisibility, ContentVisibility.PUBLIC.name());
    }

    /**
     * 解析标签筛选对应的内容 ID 集合。
     *
     * @param query 查询条件
     * @return 未指定标签返回 {@code null}；指定标签返回内容 ID 集合（可能为空）
     */
    private List<Long> resolveTagContentIds(ContentQuery query) {
        if (query.getTagId() == null || query.getTagId() <= 0) {
            return null;
        }
        return contentTagMapper.selectContentIdsByTagId(query.getTagId());
    }

    /**
     * 构造查询条件。
     *
     * @param query 查询条件
     * @param tagContentIds 标签筛选的内容 ID 集合，{@code null} 表示不筛选
     * @return 查询条件
     */
    private LambdaQueryWrapper<ContentEntity> filter(ContentQuery query, List<Long> tagContentIds) {
        LambdaQueryWrapper<ContentEntity> wrapper = new LambdaQueryWrapper<ContentEntity>()
                .eq(query.getContentType() != null, ContentEntity::getContentType,
                        nameOf(query.getContentType()))
                .eq(query.getCategoryId() != null && query.getCategoryId() > 0,
                        ContentEntity::getCategoryId, query.getCategoryId())
                .eq(query.getUploaderId() != null, ContentEntity::getUploaderId, query.getUploaderId())
                .eq(query.getCreatorId() != null && query.getCreatorId() > 0,
                        ContentEntity::getCreatorId, query.getCreatorId())
                .eq(query.getSeriesId() != null && query.getSeriesId() > 0,
                        ContentEntity::getSeriesId, query.getSeriesId())
                .eq(query.getPublishState() != null, ContentEntity::getPublishState,
                        nameOf(query.getPublishState()))
                .eq(query.getVisibility() != null, ContentEntity::getVisibility, nameOf(query.getVisibility()));
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(nested -> nested.like(ContentEntity::getTitle, query.getKeyword())
                    .or().like(ContentEntity::getSubtitle, query.getKeyword()));
        }
        if (tagContentIds != null) {
            wrapper.in(ContentEntity::getId, tagContentIds);
        }
        return wrapper;
    }

    /**
     * 构造 feed 的固定过滤条件：指定类型、已发布、公开。
     *
     * @param query feed 查询条件
     * @return 分页查询条件
     */
    private ContentQuery feedFilter(FeedQuery query) {
        ContentQuery condition = new ContentQuery();
        condition.setContentType(query.getContentType());
        condition.setCategoryId(query.getCategoryId());
        condition.setPublishState(PublishState.PUBLISHED);
        condition.setVisibility(ContentVisibility.PUBLIC);
        return condition;
    }

    /**
     * 追加键集游标条件（最新按上架时间、热门按热度分，同值再按 ID 倒序）。
     *
     * @param wrapper 查询条件
     * @param cursor 游标，{@code null} 表示首屏
     * @param hot 是否热门排序
     */
    private void applyCursor(LambdaQueryWrapper<ContentEntity> wrapper, FeedCursor cursor, boolean hot) {
        if (cursor == null) {
            return;
        }
        if (hot) {
            BigDecimal anchor = new BigDecimal(cursor.getAnchor());
            wrapper.and(nested -> nested.lt(ContentEntity::getHotScore, anchor)
                    .or(inner -> inner.eq(ContentEntity::getHotScore, anchor)
                            .lt(ContentEntity::getId, cursor.getId())));
        } else {
            LocalDateTime anchor = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(Long.parseLong(cursor.getAnchor())), ZoneId.systemDefault());
            wrapper.and(nested -> nested.lt(ContentEntity::getPublishedAt, anchor)
                    .or(inner -> inner.eq(ContentEntity::getPublishedAt, anchor)
                            .lt(ContentEntity::getId, cursor.getId())));
        }
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private Content toDomain(ContentEntity entity) {
        Content content = new Content();
        content.setId(entity.getId());
        content.setContentType(ContentType.parse(entity.getContentType()));
        content.setTitle(entity.getTitle());
        content.setSubtitle(entity.getSubtitle());
        content.setDescription(entity.getDescription());
        content.setCoverFileId(entity.getCoverFileId());
        content.setVideoFileId(entity.getVideoFileId());
        content.setDurationMs(orZero(entity.getDurationMs()));
        content.setWidth(entity.getWidth() == null ? 0 : entity.getWidth());
        content.setHeight(entity.getHeight() == null ? 0 : entity.getHeight());
        content.setFormat(entity.getFormat());
        content.setSizeBytes(orZero(entity.getSizeBytes()));
        content.setUploaderId(entity.getUploaderId());
        content.setCreatorId(orZero(entity.getCreatorId()));
        content.setCategoryId(orZero(entity.getCategoryId()));
        content.setSeriesId(orZero(entity.getSeriesId()));
        content.setEpisodeNo(entity.getEpisodeNo() == null ? 0 : entity.getEpisodeNo());
        content.setVisibility(ContentVisibility.parse(entity.getVisibility()));
        content.setPublishState(PublishState.parse(entity.getPublishState()));
        content.setCopyrightState(CopyrightState.parse(entity.getCopyrightState()));
        content.setSource(CopyrightSource.parse(entity.getSource()));
        content.setCopyrightExpireAt(entity.getCopyrightExpireAt());
        content.setTakedownReason(entity.getTakedownReason());
        content.setTakedownAt(entity.getTakedownAt());
        content.setPlayCount(orZero(entity.getPlayCount()));
        content.setLikeCount(orZero(entity.getLikeCount()));
        content.setFavoriteCount(orZero(entity.getFavoriteCount()));
        content.setCommentCount(orZero(entity.getCommentCount()));
        content.setHotScore(entity.getHotScore());
        content.setSortWeight(entity.getSortWeight() == null ? 0 : entity.getSortWeight());
        content.setPublishedAt(entity.getPublishedAt());
        content.setCreateTime(entity.getCreateTime());
        content.setUpdateTime(entity.getUpdateTime());
        return content;
    }

    /**
     * 将领域模型写入实体。
     *
     * @param entity 目标实体
     * @param content 领域模型
     */
    private void fill(ContentEntity entity, Content content) {
        entity.setContentType(nameOf(content.getContentType()));
        entity.setTitle(content.getTitle());
        entity.setSubtitle(content.getSubtitle());
        entity.setDescription(content.getDescription());
        entity.setCoverFileId(content.getCoverFileId());
        entity.setVideoFileId(content.getVideoFileId());
        entity.setDurationMs(orZero(content.getDurationMs()));
        entity.setWidth(content.getWidth() == null ? 0 : content.getWidth());
        entity.setHeight(content.getHeight() == null ? 0 : content.getHeight());
        entity.setFormat(content.getFormat());
        entity.setSizeBytes(orZero(content.getSizeBytes()));
        entity.setUploaderId(content.getUploaderId());
        entity.setCreatorId(orZero(content.getCreatorId()));
        entity.setCategoryId(orZero(content.getCategoryId()));
        entity.setSeriesId(orZero(content.getSeriesId()));
        entity.setEpisodeNo(content.getEpisodeNo() == null ? 0 : content.getEpisodeNo());
        entity.setVisibility(parseName(content.getVisibility(), ContentVisibility.PUBLIC.name()));
        entity.setPublishState(parseName(content.getPublishState(), PublishState.DRAFT.name()));
        entity.setCopyrightState(parseName(content.getCopyrightState(), CopyrightState.AUTHORIZED.name()));
        entity.setSource(nameOf(content.getSource()));
        entity.setCopyrightExpireAt(content.getCopyrightExpireAt());
        entity.setTakedownReason(content.getTakedownReason());
        entity.setTakedownAt(content.getTakedownAt());
        entity.setSortWeight(content.getSortWeight() == null ? 0 : content.getSortWeight());
        entity.setPublishedAt(content.getPublishedAt());
    }

    /**
     * 取枚举名称用于查询与写入。
     *
     * @param value 枚举值
     * @return 枚举名称；为空返回 {@code null}
     */
    private String nameOf(Enum<?> value) {
        return value == null ? null : value.name();
    }

    /**
     * 取枚举名称用于写入，空值使用兜底值。
     *
     * @param value 枚举值
     * @param fallback 兜底值
     * @return 枚举名称或兜底值
     */
    private String parseName(Enum<?> value, String fallback) {
        return value == null ? fallback : value.name();
    }

    /**
     * 空值转 0。
     *
     * @param value 原始值
     * @return 非空长整型
     */
    private long orZero(Long value) {
        return value == null ? 0L : value;
    }
}
