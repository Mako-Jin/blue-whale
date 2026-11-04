package com.bluewhale.crm.sales.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.crm.sales.domain.model.ForecastPeriodType;
import com.bluewhale.crm.sales.domain.model.ForecastScopeType;
import com.bluewhale.crm.sales.domain.model.SalesTarget;
import com.bluewhale.crm.sales.domain.model.SalesTargetQuery;
import com.bluewhale.crm.sales.domain.repository.SalesTargetRepository;
import com.bluewhale.crm.sales.infrastructure.persistence.entity.SalesTargetEntity;
import com.bluewhale.crm.sales.infrastructure.persistence.mapper.SalesTargetMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 销售目标仓储实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class SalesTargetRepositoryImpl implements SalesTargetRepository {

    private final SalesTargetMapper mapper;

    @Override
    public List<SalesTarget> page(SalesTargetQuery query) {
        Page<SalesTargetEntity> page = mapper.selectPage(query.toPage(), buildWrapper(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(SalesTargetQuery query) {
        return mapper.selectCount(buildWrapper(query));
    }

    @Override
    public SalesTarget findById(Long id) {
        SalesTargetEntity entity = mapper.selectById(id);
        return entity == null ? null : toDomain(entity);
    }

    @Override
    public SalesTarget findByPeriodScope(String period, ForecastPeriodType periodType,
                                         ForecastScopeType scopeType, Long ownerId, Long deptId) {
        SalesTargetEntity entity = mapper.selectOne(new LambdaQueryWrapper<SalesTargetEntity>()
                .eq(SalesTargetEntity::getPeriod, period)
                .eq(SalesTargetEntity::getPeriodType, periodType.name())
                .eq(SalesTargetEntity::getScopeType, scopeType.name())
                .eq(SalesTargetEntity::getOwnerId, ownerId)
                .eq(SalesTargetEntity::getDeptId, deptId)
                .last("LIMIT 1"));
        return entity == null ? null : toDomain(entity);
    }

    @Override
    public void insert(SalesTarget target) {
        SalesTargetEntity entity = toEntity(target);
        if (entity.getLockVersion() == null) {
            entity.setLockVersion(0);
        }
        mapper.insert(entity);
        target.setId(entity.getId());
        target.setLockVersion(entity.getLockVersion());
        target.setCreateTime(entity.getCreateTime());
    }

    @Override
    public boolean update(SalesTarget target) {
        SalesTargetEntity entity = toEntity(target);
        entity.setId(target.getId());
        if (entity.getLockVersion() == null) {
            entity.setLockVersion(0);
        }
        boolean updated = mapper.updateById(entity) > 0;
        if (updated) {
            target.setLockVersion(entity.getLockVersion());
        }
        return updated;
    }

    @Override
    public boolean deleteById(Long id) {
        return mapper.deleteById(id) > 0;
    }

    /**
     * 构建查询条件。
     *
     * @param query 查询条件
     * @return 查询包装器
     */
    private LambdaQueryWrapper<SalesTargetEntity> buildWrapper(SalesTargetQuery query) {
        LambdaQueryWrapper<SalesTargetEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getPeriod() != null && !query.getPeriod().isBlank(),
                SalesTargetEntity::getPeriod, query.getPeriod());
        wrapper.eq(query.getPeriodType() != null && !query.getPeriodType().isBlank(),
                SalesTargetEntity::getPeriodType, query.getPeriodType());
        wrapper.eq(query.getScopeType() != null && !query.getScopeType().isBlank(),
                SalesTargetEntity::getScopeType, query.getScopeType());
        wrapper.eq(query.getOwnerId() != null, SalesTargetEntity::getOwnerId, query.getOwnerId());
        wrapper.eq(query.getDeptId() != null, SalesTargetEntity::getDeptId, query.getDeptId());
        wrapper.orderByDesc(SalesTargetEntity::getPeriod)
                .orderByDesc(SalesTargetEntity::getCreateTime);
        return wrapper;
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private SalesTarget toDomain(SalesTargetEntity entity) {
        SalesTarget target = new SalesTarget();
        target.setId(entity.getId());
        target.setPeriod(entity.getPeriod());
        target.setPeriodType(ForecastPeriodType.from(entity.getPeriodType()));
        target.setScopeType(ForecastScopeType.from(entity.getScopeType()));
        target.setOwnerId(entity.getOwnerId());
        target.setDeptId(entity.getDeptId());
        target.setTargetAmount(entity.getTargetAmount());
        target.setRemark(entity.getRemark());
        target.setCreatedBy(entity.getCreatedBy());
        target.setLockVersion(entity.getLockVersion());
        target.setCreateTime(entity.getCreateTime());
        return target;
    }

    /**
     * 领域模型转实体。
     *
     * @param target 领域模型
     * @return 实体
     */
    private SalesTargetEntity toEntity(SalesTarget target) {
        SalesTargetEntity entity = new SalesTargetEntity();
        entity.setPeriod(target.getPeriod());
        entity.setPeriodType(target.getPeriodType() == null ? null : target.getPeriodType().name());
        entity.setScopeType(target.getScopeType() == null ? null : target.getScopeType().name());
        entity.setOwnerId(target.getOwnerId());
        entity.setDeptId(target.getDeptId());
        entity.setTargetAmount(target.getTargetAmount());
        entity.setRemark(target.getRemark());
        entity.setCreatedBy(target.getCreatedBy());
        entity.setLockVersion(target.getLockVersion());
        return entity;
    }
}
