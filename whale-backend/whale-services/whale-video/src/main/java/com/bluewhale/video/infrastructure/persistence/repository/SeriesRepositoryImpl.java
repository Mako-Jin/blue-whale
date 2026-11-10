package com.bluewhale.video.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.video.domain.model.ContentType;
import com.bluewhale.video.domain.model.PublishState;
import com.bluewhale.video.domain.model.Series;
import com.bluewhale.video.domain.model.SeriesQuery;
import com.bluewhale.video.domain.repository.SeriesRepository;
import com.bluewhale.video.infrastructure.persistence.entity.SeriesEntity;
import com.bluewhale.video.infrastructure.persistence.mapper.SeriesMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 系列仓储实现（MyBatis-Plus）。
 *
 * <p>列表按运营排序升序、同序按创建 ID 倒序；集数刷新只更新计数字段，
 * 不覆盖标题 / 状态等其他列，避免并发编辑互相覆盖。</p>
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class SeriesRepositoryImpl implements SeriesRepository {

    private final SeriesMapper seriesMapper;

    @Override
    public Optional<Series> findById(Long id) {
        return Optional.ofNullable(seriesMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public void insert(Series series) {
        SeriesEntity entity = new SeriesEntity();
        fill(entity, series);
        seriesMapper.insert(entity);
        series.setId(entity.getId());
        series.setCreateTime(entity.getCreateTime());
        series.setUpdateTime(entity.getUpdateTime());
    }

    @Override
    public void update(Series series) {
        SeriesEntity entity = new SeriesEntity();
        entity.setId(series.getId());
        fill(entity, series);
        seriesMapper.updateById(entity);
    }

    @Override
    public void delete(Long id) {
        seriesMapper.deleteById(id);
    }

    @Override
    public List<Series> page(SeriesQuery query) {
        LambdaQueryWrapper<SeriesEntity> wrapper = filter(query)
                .orderByAsc(SeriesEntity::getSortNo)
                .orderByDesc(SeriesEntity::getId);
        Page<SeriesEntity> page = seriesMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L)), wrapper);
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(SeriesQuery query) {
        return seriesMapper.selectCount(filter(query));
    }

    @Override
    public void updateEpisodeCount(Long seriesId, int episodeCount) {
        seriesMapper.update(null, new LambdaUpdateWrapper<SeriesEntity>()
                .eq(SeriesEntity::getId, seriesId)
                .set(SeriesEntity::getEpisodeCount, Math.max(episodeCount, 0)));
    }

    /**
     * 构造查询条件。
     *
     * @param query 查询条件
     * @return 查询条件
     */
    private LambdaQueryWrapper<SeriesEntity> filter(SeriesQuery query) {
        return new LambdaQueryWrapper<SeriesEntity>()
                .like(StringUtils.hasText(query.getKeyword()), SeriesEntity::getTitle, query.getKeyword())
                .eq(query.getContentType() != null, SeriesEntity::getContentType,
                        nameOf(query.getContentType()))
                .eq(query.getCategoryId() != null && query.getCategoryId() > 0,
                        SeriesEntity::getCategoryId, query.getCategoryId())
                .eq(query.getCreatorId() != null && query.getCreatorId() > 0,
                        SeriesEntity::getCreatorId, query.getCreatorId())
                .eq(query.getPublishState() != null, SeriesEntity::getPublishStatus,
                        nameOf(query.getPublishState()));
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private Series toDomain(SeriesEntity entity) {
        Series series = new Series();
        series.setId(entity.getId());
        series.setTitle(entity.getTitle());
        series.setDescription(entity.getDescription());
        series.setCoverFileId(entity.getCoverFileId());
        series.setContentType(ContentType.parse(entity.getContentType()));
        series.setCategoryId(entity.getCategoryId() == null ? 0L : entity.getCategoryId());
        series.setCreatorId(entity.getCreatorId() == null ? 0L : entity.getCreatorId());
        series.setUploaderId(entity.getUploaderId() == null ? 0L : entity.getUploaderId());
        series.setPublishState(PublishState.parse(entity.getPublishStatus()));
        series.setEpisodeCount(entity.getEpisodeCount() == null ? 0 : entity.getEpisodeCount());
        series.setSortNo(entity.getSortNo() == null ? 0 : entity.getSortNo());
        series.setPublishedAt(entity.getPublishedAt());
        series.setCreateTime(entity.getCreateTime());
        series.setUpdateTime(entity.getUpdateTime());
        return series;
    }

    /**
     * 领域模型写入实体。
     *
     * @param entity 目标实体
     * @param series 领域模型
     */
    private void fill(SeriesEntity entity, Series series) {
        entity.setTitle(series.getTitle());
        entity.setDescription(series.getDescription());
        entity.setCoverFileId(series.getCoverFileId());
        entity.setContentType(nameOf(series.getContentType()));
        entity.setCategoryId(series.getCategoryId() == null ? 0L : series.getCategoryId());
        entity.setCreatorId(series.getCreatorId() == null ? 0L : series.getCreatorId());
        entity.setUploaderId(series.getUploaderId() == null ? 0L : series.getUploaderId());
        entity.setPublishStatus(series.getPublishState() == null
                ? PublishState.DRAFT.name() : series.getPublishState().name());
        entity.setEpisodeCount(series.getEpisodeCount() == null ? 0 : series.getEpisodeCount());
        entity.setSortNo(series.getSortNo() == null ? 0 : series.getSortNo());
        entity.setPublishedAt(series.getPublishedAt());
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
}
