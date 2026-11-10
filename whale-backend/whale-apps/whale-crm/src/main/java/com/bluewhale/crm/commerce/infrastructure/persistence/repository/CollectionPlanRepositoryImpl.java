package com.bluewhale.crm.commerce.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.crm.commerce.domain.model.CollectionPlan;
import com.bluewhale.crm.commerce.domain.model.CollectionPlanStatus;
import com.bluewhale.crm.commerce.domain.model.CollectionQuery;
import com.bluewhale.crm.commerce.domain.repository.CollectionPlanRepository;
import com.bluewhale.crm.commerce.infrastructure.persistence.entity.CollectionPlanEntity;
import com.bluewhale.crm.commerce.infrastructure.persistence.mapper.CollectionPlanMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * 回款计划仓储实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class CollectionPlanRepositoryImpl implements CollectionPlanRepository {

    private final CollectionPlanMapper collectionPlanMapper;

    @Override
    public List<CollectionPlan> page(CollectionQuery query) {
        Page<CollectionPlanEntity> page = collectionPlanMapper.selectPage(query.toPage(), buildWrapper(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(CollectionQuery query) {
        return collectionPlanMapper.selectCount(buildWrapper(query));
    }

    @Override
    public CollectionPlan findById(Long id) {
        CollectionPlanEntity entity = collectionPlanMapper.selectById(id);
        return entity == null ? null : toDomain(entity);
    }

    @Override
    public List<CollectionPlan> findByContract(Long contractId) {
        return collectionPlanMapper.selectList(new LambdaQueryWrapper<CollectionPlanEntity>()
                        .eq(CollectionPlanEntity::getContractId, contractId)
                        .orderByAsc(CollectionPlanEntity::getInstallmentNo))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<CollectionPlan> replaceByContract(Long contractId, Long customerId, Long ownerId, Long deptId,
                                                  List<CollectionPlan> plans) {
        collectionPlanMapper.delete(new LambdaQueryWrapper<CollectionPlanEntity>()
                .eq(CollectionPlanEntity::getContractId, contractId));
        for (CollectionPlan plan : plans) {
            CollectionPlanEntity entity = toEntity(plan);
            entity.setContractId(contractId);
            entity.setCustomerId(customerId);
            entity.setOwnerId(ownerId);
            entity.setDeptId(deptId);
            entity.setStatus(plan.getStatus() == null
                    ? CollectionPlanStatus.OPEN.name() : plan.getStatus().name());
            if (entity.getLockVersion() == null) {
                entity.setLockVersion(0);
            }
            collectionPlanMapper.insert(entity);
            plan.setId(entity.getId());
            plan.setContractId(contractId);
            plan.setCustomerId(customerId);
            plan.setOwnerId(ownerId);
            plan.setDeptId(deptId);
            plan.setLockVersion(entity.getLockVersion());
            plan.setCreateTime(entity.getCreateTime());
        }
        return plans;
    }

    @Override
    public boolean updatePromise(CollectionPlan plan) {
        LambdaUpdateWrapper<CollectionPlanEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(CollectionPlanEntity::getId, plan.getId())
                .set(CollectionPlanEntity::getPromiseDate, plan.getPromiseDate())
                .set(CollectionPlanEntity::getPromiseAmount, plan.getPromiseAmount())
                .set(CollectionPlanEntity::getRemark, plan.getRemark());
        return collectionPlanMapper.update(null, wrapper) > 0;
    }

    @Override
    public boolean update(CollectionPlan plan) {
        CollectionPlanEntity entity = toEntity(plan);
        entity.setId(plan.getId());
        if (entity.getLockVersion() == null) {
            entity.setLockVersion(0);
        }
        boolean updated = collectionPlanMapper.updateById(entity) > 0;
        if (updated) {
            plan.setLockVersion(entity.getLockVersion());
        }
        return updated;
    }

    /**
     * 构建查询条件。
     *
     * @param query 查询条件
     * @return 查询包装器
     */
    private LambdaQueryWrapper<CollectionPlanEntity> buildWrapper(CollectionQuery query) {
        LambdaQueryWrapper<CollectionPlanEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getContractId() != null, CollectionPlanEntity::getContractId, query.getContractId());
        wrapper.eq(query.getCustomerId() != null, CollectionPlanEntity::getCustomerId, query.getCustomerId());
        wrapper.eq(query.getStatus() != null && !query.getStatus().isBlank(),
                CollectionPlanEntity::getStatus, query.getStatus());
        if (Boolean.TRUE.equals(query.getOverdueOnly())) {
            wrapper.lt(CollectionPlanEntity::getPlannedDate, LocalDate.now());
        }
        wrapper.ge(query.getPlannedFrom() != null, CollectionPlanEntity::getPlannedDate, query.getPlannedFrom());
        wrapper.le(query.getPlannedTo() != null, CollectionPlanEntity::getPlannedDate, query.getPlannedTo());
        wrapper.eq(query.getOwnerId() != null, CollectionPlanEntity::getOwnerId, query.getOwnerId());
        wrapper.orderByAsc(CollectionPlanEntity::getPlannedDate)
                .orderByAsc(CollectionPlanEntity::getInstallmentNo);
        return wrapper;
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private CollectionPlan toDomain(CollectionPlanEntity entity) {
        CollectionPlan plan = new CollectionPlan();
        plan.setId(entity.getId());
        plan.setContractId(entity.getContractId());
        plan.setCustomerId(entity.getCustomerId());
        plan.setInstallmentNo(entity.getInstallmentNo());
        plan.setPlannedAmount(entity.getPlannedAmount());
        plan.setPlannedDate(entity.getPlannedDate());
        plan.setStatus(entity.getStatus() == null
                ? CollectionPlanStatus.OPEN : CollectionPlanStatus.valueOf(entity.getStatus()));
        plan.setPriorityOverride(entity.getPriorityOverride());
        plan.setPromiseDate(entity.getPromiseDate());
        plan.setPromiseAmount(entity.getPromiseAmount());
        plan.setOwnerId(entity.getOwnerId());
        plan.setDeptId(entity.getDeptId());
        plan.setRemark(entity.getRemark());
        plan.setLockVersion(entity.getLockVersion());
        plan.setCreateTime(entity.getCreateTime());
        plan.setUpdateTime(entity.getUpdateTime());
        return plan;
    }

    /**
     * 领域模型转实体。
     *
     * @param plan 领域模型
     * @return 实体
     */
    private CollectionPlanEntity toEntity(CollectionPlan plan) {
        CollectionPlanEntity entity = new CollectionPlanEntity();
        entity.setInstallmentNo(plan.getInstallmentNo());
        entity.setPlannedAmount(plan.getPlannedAmount());
        entity.setPlannedDate(plan.getPlannedDate());
        entity.setStatus(plan.getStatus() == null ? null : plan.getStatus().name());
        entity.setPriorityOverride(plan.getPriorityOverride());
        entity.setPromiseDate(plan.getPromiseDate());
        entity.setPromiseAmount(plan.getPromiseAmount());
        entity.setRemark(plan.getRemark());
        entity.setLockVersion(plan.getLockVersion());
        return entity;
    }
}
