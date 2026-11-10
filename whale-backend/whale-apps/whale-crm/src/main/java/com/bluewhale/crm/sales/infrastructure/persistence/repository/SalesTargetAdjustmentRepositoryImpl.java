package com.bluewhale.crm.sales.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.crm.sales.domain.model.SalesTargetAdjustment;
import com.bluewhale.crm.sales.domain.repository.SalesTargetAdjustmentRepository;
import com.bluewhale.crm.sales.infrastructure.persistence.entity.SalesTargetAdjustmentEntity;
import com.bluewhale.crm.sales.infrastructure.persistence.mapper.SalesTargetAdjustmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 销售目标调整记录仓储实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class SalesTargetAdjustmentRepositoryImpl implements SalesTargetAdjustmentRepository {

    private final SalesTargetAdjustmentMapper mapper;

    @Override
    public void insert(SalesTargetAdjustment adjustment) {
        SalesTargetAdjustmentEntity entity = toEntity(adjustment);
        mapper.insert(entity);
        adjustment.setId(entity.getId());
        adjustment.setCreateTime(entity.getCreateTime());
    }

    @Override
    public List<SalesTargetAdjustment> listByTarget(Long targetId) {
        return mapper.selectList(new LambdaQueryWrapper<SalesTargetAdjustmentEntity>()
                        .eq(SalesTargetAdjustmentEntity::getTargetId, targetId)
                        .orderByDesc(SalesTargetAdjustmentEntity::getAdjustedAt))
                .stream().map(this::toDomain).toList();
    }

    /**
     * 领域模型转实体。
     *
     * @param adjustment 调整记录
     * @return 实体
     */
    private SalesTargetAdjustmentEntity toEntity(SalesTargetAdjustment adjustment) {
        SalesTargetAdjustmentEntity entity = new SalesTargetAdjustmentEntity();
        entity.setTargetId(adjustment.getTargetId());
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
    private SalesTargetAdjustment toDomain(SalesTargetAdjustmentEntity entity) {
        SalesTargetAdjustment adjustment = new SalesTargetAdjustment();
        adjustment.setId(entity.getId());
        adjustment.setTargetId(entity.getTargetId());
        adjustment.setBeforeAmount(entity.getBeforeAmount());
        adjustment.setAfterAmount(entity.getAfterAmount());
        adjustment.setReason(entity.getReason());
        adjustment.setOperatorId(entity.getOperatorId());
        adjustment.setAdjustedAt(entity.getAdjustedAt());
        adjustment.setCreateTime(entity.getCreateTime());
        return adjustment;
    }
}
