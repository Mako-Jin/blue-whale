package com.bluewhale.crm.sales.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.crm.sales.domain.model.ForecastPeriodType;
import com.bluewhale.crm.sales.domain.model.ForecastQuery;
import com.bluewhale.crm.sales.domain.model.ForecastScopeType;
import com.bluewhale.crm.sales.domain.model.ForecastSnapshot;
import com.bluewhale.crm.sales.domain.repository.ForecastSnapshotRepository;
import com.bluewhale.crm.sales.infrastructure.persistence.entity.ForecastSnapshotEntity;
import com.bluewhale.crm.sales.infrastructure.persistence.mapper.ForecastSnapshotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 销售预测快照仓储实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class ForecastSnapshotRepositoryImpl implements ForecastSnapshotRepository {

    private final ForecastSnapshotMapper mapper;

    @Override
    public List<ForecastSnapshot> page(ForecastQuery query) {
        Page<ForecastSnapshotEntity> page = mapper.selectPage(query.toPage(), buildWrapper(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(ForecastQuery query) {
        return mapper.selectCount(buildWrapper(query));
    }

    @Override
    public ForecastSnapshot findById(Long id) {
        ForecastSnapshotEntity entity = mapper.selectById(id);
        return entity == null ? null : toDomain(entity);
    }

    @Override
    public ForecastSnapshot findByPeriodScope(String period, ForecastPeriodType periodType,
                                              ForecastScopeType scopeType, Long ownerId, Long deptId) {
        ForecastSnapshotEntity entity = mapper.selectOne(new LambdaQueryWrapper<ForecastSnapshotEntity>()
                .eq(ForecastSnapshotEntity::getPeriod, period)
                .eq(ForecastSnapshotEntity::getPeriodType, periodType.name())
                .eq(ForecastSnapshotEntity::getScopeType, scopeType.name())
                .eq(ForecastSnapshotEntity::getOwnerId, ownerId)
                .eq(ForecastSnapshotEntity::getDeptId, deptId)
                .last("LIMIT 1"));
        return entity == null ? null : toDomain(entity);
    }

    @Override
    public void insert(ForecastSnapshot snapshot) {
        ForecastSnapshotEntity entity = toEntity(snapshot);
        if (entity.getLockVersion() == null) {
            entity.setLockVersion(0);
        }
        if (entity.getManualAdjusted() == null) {
            entity.setManualAdjusted(0);
        }
        mapper.insert(entity);
        snapshot.setId(entity.getId());
        snapshot.setLockVersion(entity.getLockVersion());
        snapshot.setCreateTime(entity.getCreateTime());
    }

    @Override
    public boolean update(ForecastSnapshot snapshot) {
        ForecastSnapshotEntity entity = toEntity(snapshot);
        entity.setId(snapshot.getId());
        if (entity.getLockVersion() == null) {
            entity.setLockVersion(0);
        }
        boolean updated = mapper.updateById(entity) > 0;
        if (updated) {
            snapshot.setLockVersion(entity.getLockVersion());
        }
        return updated;
    }

    /**
     * 构建查询条件。
     *
     * @param query 查询条件
     * @return 查询包装器
     */
    private LambdaQueryWrapper<ForecastSnapshotEntity> buildWrapper(ForecastQuery query) {
        LambdaQueryWrapper<ForecastSnapshotEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getPeriod() != null && !query.getPeriod().isBlank(),
                ForecastSnapshotEntity::getPeriod, query.getPeriod());
        wrapper.eq(query.getPeriodType() != null && !query.getPeriodType().isBlank(),
                ForecastSnapshotEntity::getPeriodType, query.getPeriodType());
        wrapper.eq(query.getScopeType() != null && !query.getScopeType().isBlank(),
                ForecastSnapshotEntity::getScopeType, query.getScopeType());
        wrapper.eq(query.getOwnerId() != null, ForecastSnapshotEntity::getOwnerId, query.getOwnerId());
        wrapper.eq(query.getDeptId() != null, ForecastSnapshotEntity::getDeptId, query.getDeptId());
        wrapper.orderByDesc(ForecastSnapshotEntity::getPeriod)
                .orderByDesc(ForecastSnapshotEntity::getCreateTime);
        return wrapper;
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private ForecastSnapshot toDomain(ForecastSnapshotEntity entity) {
        ForecastSnapshot snapshot = new ForecastSnapshot();
        snapshot.setId(entity.getId());
        snapshot.setPeriod(entity.getPeriod());
        snapshot.setPeriodType(ForecastPeriodType.from(entity.getPeriodType()));
        snapshot.setScopeType(ForecastScopeType.from(entity.getScopeType()));
        snapshot.setOwnerId(entity.getOwnerId());
        snapshot.setDeptId(entity.getDeptId());
        snapshot.setPipelineAmount(entity.getPipelineAmount());
        snapshot.setBestAmount(entity.getBestAmount());
        snapshot.setCommitAmount(entity.getCommitAmount());
        snapshot.setWeightedAmount(entity.getWeightedAmount());
        snapshot.setOpportunityCount(entity.getOpportunityCount());
        snapshot.setManualAdjusted(entity.getManualAdjusted() != null && entity.getManualAdjusted() > 0);
        snapshot.setRemark(entity.getRemark());
        snapshot.setCreatedBy(entity.getCreatedBy());
        snapshot.setLockVersion(entity.getLockVersion());
        snapshot.setCreateTime(entity.getCreateTime());
        snapshot.setUpdateTime(entity.getUpdateTime());
        return snapshot;
    }

    /**
     * 领域模型转实体。
     *
     * @param snapshot 领域模型
     * @return 实体
     */
    private ForecastSnapshotEntity toEntity(ForecastSnapshot snapshot) {
        ForecastSnapshotEntity entity = new ForecastSnapshotEntity();
        entity.setPeriod(snapshot.getPeriod());
        entity.setPeriodType(snapshot.getPeriodType() == null ? null : snapshot.getPeriodType().name());
        entity.setScopeType(snapshot.getScopeType() == null ? null : snapshot.getScopeType().name());
        entity.setOwnerId(snapshot.getOwnerId());
        entity.setDeptId(snapshot.getDeptId());
        entity.setPipelineAmount(snapshot.getPipelineAmount());
        entity.setBestAmount(snapshot.getBestAmount());
        entity.setCommitAmount(snapshot.getCommitAmount());
        entity.setWeightedAmount(snapshot.getWeightedAmount());
        entity.setOpportunityCount(snapshot.getOpportunityCount());
        entity.setManualAdjusted(Boolean.TRUE.equals(snapshot.getManualAdjusted()) ? 1 : 0);
        entity.setRemark(snapshot.getRemark());
        entity.setCreatedBy(snapshot.getCreatedBy());
        entity.setLockVersion(snapshot.getLockVersion());
        return entity;
    }
}
