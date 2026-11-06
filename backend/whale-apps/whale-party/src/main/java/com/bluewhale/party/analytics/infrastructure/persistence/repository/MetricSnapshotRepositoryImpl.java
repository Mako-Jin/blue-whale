package com.bluewhale.party.analytics.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.party.analytics.domain.model.MetricScope;
import com.bluewhale.party.analytics.domain.model.MetricSnapshot;
import com.bluewhale.party.analytics.domain.repository.MetricSnapshotRepository;
import com.bluewhale.party.analytics.infrastructure.persistence.entity.MetricSnapshotEntity;
import com.bluewhale.party.analytics.infrastructure.persistence.mapper.MetricSnapshotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * 统计快照仓储实现（MyBatis-Plus）。
 *
 * <p>覆盖写按「先按键查、命中则更新」实现而不是先删后插：删除会短暂留下空窗口，
 * 看板在这个窗口里读到的是缺数据；先查后更新最坏只是更新稍微晚一点，读端始终有值。</p>
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class MetricSnapshotRepositoryImpl implements MetricSnapshotRepository {

    private final MetricSnapshotMapper metricSnapshotMapper;

    @Override
    public List<MetricSnapshot> listByDate(LocalDate statDate) {
        return metricSnapshotMapper.selectList(new LambdaQueryWrapper<MetricSnapshotEntity>()
                        .eq(MetricSnapshotEntity::getStatDate, statDate)
                        .orderByAsc(MetricSnapshotEntity::getScopeType)
                        .orderByAsc(MetricSnapshotEntity::getScopeId)
                        .orderByAsc(MetricSnapshotEntity::getMetricType))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<MetricSnapshot> listByDate(LocalDate statDate, MetricScope scope, Long scopeId) {
        return metricSnapshotMapper.selectList(new LambdaQueryWrapper<MetricSnapshotEntity>()
                        .eq(MetricSnapshotEntity::getStatDate, statDate)
                        .eq(MetricSnapshotEntity::getScopeType, scope.name())
                        .eq(MetricSnapshotEntity::getScopeId, scopeId == null
                                ? MetricSnapshot.ALL_SCOPE_ID : scopeId)
                        .orderByAsc(MetricSnapshotEntity::getMetricType))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<MetricSnapshot> listTrend(LocalDate from, LocalDate to, String metricType,
                                          MetricScope scope, Long scopeId) {
        return metricSnapshotMapper.selectList(new LambdaQueryWrapper<MetricSnapshotEntity>()
                        .ge(MetricSnapshotEntity::getStatDate, from)
                        .le(MetricSnapshotEntity::getStatDate, to)
                        .eq(MetricSnapshotEntity::getMetricType, metricType)
                        .eq(MetricSnapshotEntity::getScopeType, scope.name())
                        .eq(MetricSnapshotEntity::getScopeId, scopeId == null
                                ? MetricSnapshot.ALL_SCOPE_ID : scopeId)
                        .orderByAsc(MetricSnapshotEntity::getStatDate))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<MetricSnapshot> listBetween(LocalDate from, LocalDate to, MetricScope scope, Long scopeId) {
        return metricSnapshotMapper.selectList(new LambdaQueryWrapper<MetricSnapshotEntity>()
                        .ge(MetricSnapshotEntity::getStatDate, from)
                        .le(MetricSnapshotEntity::getStatDate, to)
                        .eq(MetricSnapshotEntity::getScopeType, scope.name())
                        .eq(MetricSnapshotEntity::getScopeId, scopeId == null
                                ? MetricSnapshot.ALL_SCOPE_ID : scopeId)
                        .orderByAsc(MetricSnapshotEntity::getStatDate)
                        .orderByAsc(MetricSnapshotEntity::getMetricType))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public void saveAll(List<MetricSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return;
        }
        for (MetricSnapshot snapshot : snapshots) {
            MetricSnapshotEntity existing = metricSnapshotMapper.selectOne(
                    new LambdaQueryWrapper<MetricSnapshotEntity>()
                            .eq(MetricSnapshotEntity::getStatDate, snapshot.getStatDate())
                            .eq(MetricSnapshotEntity::getMetricType, snapshot.getMetricType())
                            .eq(MetricSnapshotEntity::getScopeType, snapshot.getScopeType())
                            .eq(MetricSnapshotEntity::getScopeId, snapshot.getScopeId())
                            .last("LIMIT 1"));
            MetricSnapshotEntity entity = toEntity(snapshot);
            if (existing == null) {
                metricSnapshotMapper.insert(entity);
            } else {
                entity.setId(existing.getId());
                metricSnapshotMapper.updateById(entity);
            }
            snapshot.setId(entity.getId());
        }
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private MetricSnapshot toDomain(MetricSnapshotEntity entity) {
        MetricSnapshot snapshot = new MetricSnapshot();
        snapshot.setId(entity.getId());
        snapshot.setStatDate(entity.getStatDate());
        snapshot.setMetricType(entity.getMetricType());
        snapshot.setScopeType(entity.getScopeType());
        snapshot.setScopeId(entity.getScopeId());
        snapshot.setScopeName(entity.getScopeName());
        snapshot.setNumerator(entity.getNumerator());
        snapshot.setDenominator(entity.getDenominator());
        snapshot.setComputedAt(entity.getComputedAt());
        return snapshot;
    }

    /**
     * 领域模型转实体。
     *
     * @param snapshot 领域模型
     * @return 实体
     */
    private MetricSnapshotEntity toEntity(MetricSnapshot snapshot) {
        MetricSnapshotEntity entity = new MetricSnapshotEntity();
        entity.setStatDate(snapshot.getStatDate());
        entity.setMetricType(snapshot.getMetricType());
        entity.setScopeType(snapshot.getScopeType());
        entity.setScopeId(snapshot.getScopeId());
        entity.setScopeName(snapshot.getScopeName());
        entity.setNumerator(snapshot.getNumerator());
        entity.setDenominator(snapshot.getDenominator());
        entity.setComputedAt(snapshot.getComputedAt());
        return entity;
    }
}
