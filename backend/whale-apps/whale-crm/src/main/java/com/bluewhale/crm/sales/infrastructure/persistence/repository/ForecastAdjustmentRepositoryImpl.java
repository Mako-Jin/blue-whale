package com.bluewhale.crm.sales.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.crm.sales.domain.model.ForecastAdjustment;
import com.bluewhale.crm.sales.domain.repository.ForecastAdjustmentRepository;
import com.bluewhale.crm.sales.infrastructure.persistence.entity.ForecastAdjustmentEntity;
import com.bluewhale.crm.sales.infrastructure.persistence.mapper.ForecastAdjustmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 预测调整记录仓储实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class ForecastAdjustmentRepositoryImpl implements ForecastAdjustmentRepository {

    private final ForecastAdjustmentMapper mapper;

    @Override
    public void insert(ForecastAdjustment adjustment) {
        ForecastAdjustmentEntity entity = toEntity(adjustment);
        mapper.insert(entity);
        adjustment.setId(entity.getId());
        adjustment.setCreateTime(entity.getCreateTime());
    }

    @Override
    public List<ForecastAdjustment> listBySnapshot(Long snapshotId) {
        return mapper.selectList(new LambdaQueryWrapper<ForecastAdjustmentEntity>()
                        .eq(ForecastAdjustmentEntity::getSnapshotId, snapshotId)
                        .orderByDesc(ForecastAdjustmentEntity::getAdjustedAt))
                .stream().map(this::toDomain).toList();
    }

    /**
     * 领域模型转实体。
     *
     * @param adjustment 调整记录
     * @return 实体
     */
    private ForecastAdjustmentEntity toEntity(ForecastAdjustment adjustment) {
        ForecastAdjustmentEntity entity = new ForecastAdjustmentEntity();
        entity.setSnapshotId(adjustment.getSnapshotId());
        entity.setFieldCode(adjustment.getFieldCode());
        entity.setBeforeAmount(adjustment.getBeforeAmount());
        entity.setAfterAmount(adjustment.getAfterAmount());
        entity.setReason(adjustment.getReason());
        entity.setOperatorId(adjustment.getOperatorId());
        entity.setAdjustedAt(adjustment.getAdjustedAt());
        return entity;
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private ForecastAdjustment toDomain(ForecastAdjustmentEntity entity) {
        ForecastAdjustment adjustment = new ForecastAdjustment();
        adjustment.setId(entity.getId());
        adjustment.setSnapshotId(entity.getSnapshotId());
        adjustment.setFieldCode(entity.getFieldCode());
        adjustment.setBeforeAmount(entity.getBeforeAmount());
        adjustment.setAfterAmount(entity.getAfterAmount());
        adjustment.setReason(entity.getReason());
        adjustment.setOperatorId(entity.getOperatorId());
        adjustment.setAdjustedAt(entity.getAdjustedAt());
        adjustment.setCreateTime(entity.getCreateTime());
        return adjustment;
    }
}
