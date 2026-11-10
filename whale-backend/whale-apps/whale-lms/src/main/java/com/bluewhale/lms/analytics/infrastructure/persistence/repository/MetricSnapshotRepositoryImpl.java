package com.bluewhale.lms.analytics.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.lms.analytics.domain.model.MetricQuery;
import com.bluewhale.lms.analytics.domain.model.MetricScope;
import com.bluewhale.lms.analytics.domain.model.MetricSnapshot;
import com.bluewhale.lms.analytics.domain.repository.MetricSnapshotRepository;
import com.bluewhale.lms.analytics.infrastructure.persistence.entity.MetricSnapshotEntity;
import com.bluewhale.lms.analytics.infrastructure.persistence.mapper.MetricSnapshotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 指标快照仓储实现（MyBatis-Plus）。
 *
 * <p>写入是「同统计日同范围覆盖」而不是追加：追加会让同一天出现多份快照，
 * 看板不知道该信哪一份；覆盖则补跑天然幂等，不同统计日的行各自保留形成趋势。</p>
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class MetricSnapshotRepositoryImpl implements MetricSnapshotRepository {

    private final MetricSnapshotMapper metricSnapshotMapper;

    @Override
    public Optional<MetricSnapshot> find(LocalDate statDate, MetricScope scopeType, Long scopeId) {
        return Optional.ofNullable(metricSnapshotMapper.selectOne(new LambdaQueryWrapper<MetricSnapshotEntity>()
                        .eq(MetricSnapshotEntity::getStatDate, statDate)
                        .eq(MetricSnapshotEntity::getScopeType, scopeType.name())
                        .eq(MetricSnapshotEntity::getScopeId, scopeId == null ? 0L : scopeId)))
                .map(this::toDomain);
    }

    @Override
    public Long save(MetricSnapshot snapshot) {
        MetricSnapshotEntity existing = metricSnapshotMapper.selectOne(
                new LambdaQueryWrapper<MetricSnapshotEntity>()
                        .eq(MetricSnapshotEntity::getStatDate, snapshot.getStatDate())
                        .eq(MetricSnapshotEntity::getScopeType, snapshot.getScopeType().name())
                        .eq(MetricSnapshotEntity::getScopeId, snapshot.getScopeId()));
        MetricSnapshotEntity entity = toEntity(snapshot);
        if (existing == null) {
            metricSnapshotMapper.insert(entity);
        } else {
            entity.setId(existing.getId());
            metricSnapshotMapper.updateById(entity);
        }
        snapshot.setId(entity.getId());
        return entity.getId();
    }

    @Override
    public List<MetricSnapshot> page(MetricQuery query, long offset, int size) {
        long current = Math.max(offset / Math.max(size, 1) + 1, 1L);
        Page<MetricSnapshotEntity> page = metricSnapshotMapper.selectPage(
                new Page<>(current, Math.max(size, 1)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(MetricQuery query) {
        return metricSnapshotMapper.selectCount(filter(query));
    }

    @Override
    public List<MetricSnapshot> listRecentAll(int days) {
        int limit = Math.max(days, 1);
        List<MetricSnapshotEntity> entities = metricSnapshotMapper.selectList(
                new LambdaQueryWrapper<MetricSnapshotEntity>()
                        .eq(MetricSnapshotEntity::getScopeType, MetricScope.ALL.name())
                        .orderByDesc(MetricSnapshotEntity::getStatDate)
                        .last("LIMIT " + limit));
        List<MetricSnapshot> snapshots = new ArrayList<>(entities.stream().map(this::toDomain).toList());
        // 查询按日期倒序取最近 N 天，趋势图要按时间升序看，这里翻一次
        Collections.reverse(snapshots);
        return snapshots;
    }

    /**
     * 组装查询条件。
     *
     * @param query 查询条件
     * @return 包装器
     */
    private LambdaQueryWrapper<MetricSnapshotEntity> filter(MetricQuery query) {
        LambdaQueryWrapper<MetricSnapshotEntity> wrapper = new LambdaQueryWrapper<>();
        if (query.getFrom() != null) {
            wrapper.ge(MetricSnapshotEntity::getStatDate, query.getFrom());
        }
        if (query.getTo() != null) {
            wrapper.le(MetricSnapshotEntity::getStatDate, query.getTo());
        }
        if (StringUtils.hasText(query.getScopeType())) {
            wrapper.eq(MetricSnapshotEntity::getScopeType, query.getScopeType().trim().toUpperCase());
        }
        wrapper.orderByDesc(MetricSnapshotEntity::getStatDate);
        return wrapper;
    }

    /**
     * 实体转领域。
     *
     * @param entity 实体
     * @return 快照
     */
    private MetricSnapshot toDomain(MetricSnapshotEntity entity) {
        MetricSnapshot snapshot = new MetricSnapshot();
        snapshot.setId(entity.getId());
        snapshot.setStatDate(entity.getStatDate());
        snapshot.setScopeType(MetricScope.require(entity.getScopeType()));
        snapshot.setScopeId(entity.getScopeId() == null ? 0L : entity.getScopeId());
        snapshot.setScopeName(entity.getScopeName());
        snapshot.setMemberTotal(intOf(entity.getMemberTotal()));
        snapshot.setLearnerTotal(intOf(entity.getLearnerTotal()));
        snapshot.setLearnerCompleted(intOf(entity.getLearnerCompleted()));
        snapshot.setExamTakerTotal(intOf(entity.getExamTakerTotal()));
        snapshot.setExamPassedTotal(intOf(entity.getExamPassedTotal()));
        snapshot.setCreditTotal(entity.getCreditTotal() == null ? BigDecimal.ZERO : entity.getCreditTotal());
        snapshot.setCreditEarners(intOf(entity.getCreditEarners()));
        snapshot.setComputedAt(entity.getComputedAt());
        snapshot.setCreateTime(entity.getCreateTime());
        snapshot.setUpdateTime(entity.getUpdateTime());
        return snapshot;
    }

    /**
     * 领域转实体。
     *
     * @param snapshot 快照
     * @return 实体
     */
    private MetricSnapshotEntity toEntity(MetricSnapshot snapshot) {
        MetricSnapshotEntity entity = new MetricSnapshotEntity();
        entity.setStatDate(snapshot.getStatDate());
        entity.setScopeType(snapshot.getScopeType().name());
        entity.setScopeId(snapshot.getScopeId());
        entity.setScopeName(snapshot.getScopeName());
        entity.setMemberTotal((int) snapshot.getMemberTotal());
        entity.setLearnerTotal((int) snapshot.getLearnerTotal());
        entity.setLearnerCompleted((int) snapshot.getLearnerCompleted());
        entity.setExamTakerTotal((int) snapshot.getExamTakerTotal());
        entity.setExamPassedTotal((int) snapshot.getExamPassedTotal());
        entity.setCreditTotal(snapshot.getCreditTotal());
        entity.setCreditEarners((int) snapshot.getCreditEarners());
        entity.setComputedAt(snapshot.getComputedAt());
        return entity;
    }

    /**
     * 空值安全的整数转换。
     *
     * @param value 原值
     * @return 非负整数
     */
    private int intOf(Integer value) {
        return value == null ? 0 : value;
    }
}
