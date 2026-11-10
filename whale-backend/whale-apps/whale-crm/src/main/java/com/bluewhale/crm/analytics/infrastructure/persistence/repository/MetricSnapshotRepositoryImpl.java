package com.bluewhale.crm.analytics.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.crm.analytics.domain.model.DimensionType;
import com.bluewhale.crm.analytics.domain.model.MetricCode;
import com.bluewhale.crm.analytics.domain.model.MetricSnapshot;
import com.bluewhale.crm.analytics.domain.model.MetricSnapshotQuery;
import com.bluewhale.crm.analytics.domain.repository.MetricSnapshotRepository;
import com.bluewhale.crm.analytics.infrastructure.persistence.entity.MetricSnapshotEntity;
import com.bluewhale.crm.analytics.infrastructure.persistence.mapper.MetricSnapshotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 指标快照仓储实现。
 *
 * <p>写入仅做 update / insert，不执行删除；同一唯一键重复写入时更新原记录。</p>
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class MetricSnapshotRepositoryImpl implements MetricSnapshotRepository {

    /** 默认租户 */
    private static final long DEFAULT_TENANT_ID = 1L;

    private final MetricSnapshotMapper metricSnapshotMapper;

    /**
     * 按唯一键幂等写入。
     *
     * @param snapshot 指标快照
     */
    @Override
    public void upsert(MetricSnapshot snapshot) {
        if (snapshot.getTenantId() == null) {
            snapshot.setTenantId(DEFAULT_TENANT_ID);
        }
        MetricSnapshotEntity existing = metricSnapshotMapper.selectOne(uniqueWrapper(snapshot));
        MetricSnapshotEntity entity = toEntity(snapshot);
        if (existing != null) {
            entity.setId(existing.getId());
            metricSnapshotMapper.updateById(entity);
        } else {
            metricSnapshotMapper.insert(entity);
        }
    }

    /**
     * 按唯一键查询。
     *
     * @param tenantId 租户 ID
     * @param snapshot 快照条件
     * @return 快照
     */
    @Override
    public MetricSnapshot findByUniqueKey(Long tenantId, MetricSnapshot snapshot) {
        MetricSnapshotEntity entity = metricSnapshotMapper.selectOne(
                uniqueWrapper(tenantId == null ? DEFAULT_TENANT_ID : tenantId, snapshot));
        return entity == null ? null : toDomain(entity);
    }

    /**
     * 分页查询。
     *
     * @param query 查询条件
     * @return 快照列表
     */
    @Override
    public List<MetricSnapshot> page(MetricSnapshotQuery query) {
        Page<MetricSnapshotEntity> page = new Page<>(query.getCurrent(), query.getSize());
        return metricSnapshotMapper.selectPage(page, pageWrapper(query))
                .getRecords().stream().map(this::toDomain).toList();
    }

    /**
     * 查询总数。
     *
     * @param query 查询条件
     * @return 总数
     */
    @Override
    public long count(MetricSnapshotQuery query) {
        return metricSnapshotMapper.selectCount(pageWrapper(query));
    }

    /**
     * 构造唯一键查询条件。
     *
     * @param snapshot 快照
     * @return 查询包装器
     */
    private LambdaQueryWrapper<MetricSnapshotEntity> uniqueWrapper(MetricSnapshot snapshot) {
        return uniqueWrapper(snapshot.getTenantId(), snapshot);
    }

    /**
     * 构造唯一键查询条件。
     *
     * @param tenantId 租户 ID
     * @param snapshot 快照
     * @return 查询包装器
     */
    private LambdaQueryWrapper<MetricSnapshotEntity> uniqueWrapper(Long tenantId, MetricSnapshot snapshot) {
        return new LambdaQueryWrapper<MetricSnapshotEntity>()
                .eq(MetricSnapshotEntity::getTenantId, tenantId)
                .eq(MetricSnapshotEntity::getMetricCode, snapshot.getMetricCode().name())
                .eq(MetricSnapshotEntity::getStatDate, snapshot.getStatDate())
                .eq(MetricSnapshotEntity::getDimensionType, snapshot.getDimensionType().name())
                .eq(MetricSnapshotEntity::getDimensionId, snapshot.getDimensionId());
    }

    /**
     * 构造分页查询条件。
     *
     * @param query 查询条件
     * @return 查询包装器
     */
    private LambdaQueryWrapper<MetricSnapshotEntity> pageWrapper(MetricSnapshotQuery query) {
        return new LambdaQueryWrapper<MetricSnapshotEntity>()
                .eq(StringUtils.hasText(query.getMetricCode()),
                        MetricSnapshotEntity::getMetricCode, query.getMetricCode())
                .eq(StringUtils.hasText(query.getDimensionType()),
                        MetricSnapshotEntity::getDimensionType, query.getDimensionType())
                .eq(StringUtils.hasText(query.getDimensionId()),
                        MetricSnapshotEntity::getDimensionId, query.getDimensionId())
                .ge(query.getStartDate() != null, MetricSnapshotEntity::getStatDate, query.getStartDate())
                .le(query.getEndDate() != null, MetricSnapshotEntity::getStatDate, query.getEndDate())
                .orderByDesc(MetricSnapshotEntity::getStatDate)
                .orderByAsc(MetricSnapshotEntity::getMetricCode);
    }

    /**
     * 实体转领域对象。
     *
     * @param entity 实体
     * @return 快照
     */
    private MetricSnapshot toDomain(MetricSnapshotEntity entity) {
        MetricSnapshot snapshot = new MetricSnapshot();
        snapshot.setId(entity.getId());
        snapshot.setTenantId(entity.getTenantId());
        snapshot.setMetricCode(MetricCode.from(entity.getMetricCode()));
        snapshot.setStatDate(entity.getStatDate());
        snapshot.setDimensionType(DimensionType.from(entity.getDimensionType()));
        snapshot.setDimensionId(entity.getDimensionId());
        snapshot.setMetricValue(entity.getMetricValue());
        snapshot.setMetricExtra(entity.getMetricExtra());
        snapshot.setCreateTime(entity.getCreateTime());
        snapshot.setUpdateTime(entity.getUpdateTime());
        return snapshot;
    }

    /**
     * 领域对象转实体。
     *
     * @param snapshot 快照
     * @return 实体
     */
    private MetricSnapshotEntity toEntity(MetricSnapshot snapshot) {
        MetricSnapshotEntity entity = new MetricSnapshotEntity();
        entity.setId(snapshot.getId());
        entity.setTenantId(snapshot.getTenantId());
        entity.setMetricCode(snapshot.getMetricCode().name());
        entity.setStatDate(snapshot.getStatDate());
        entity.setDimensionType(snapshot.getDimensionType().name());
        entity.setDimensionId(snapshot.getDimensionId());
        entity.setMetricValue(snapshot.getMetricValue());
        entity.setMetricExtra(snapshot.getMetricExtra());
        return entity;
    }
}
