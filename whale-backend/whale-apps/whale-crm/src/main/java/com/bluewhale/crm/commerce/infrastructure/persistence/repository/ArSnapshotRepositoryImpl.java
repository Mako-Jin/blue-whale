package com.bluewhale.crm.commerce.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.crm.commerce.domain.model.ArSnapshot;
import com.bluewhale.crm.commerce.domain.model.ArSnapshotQuery;
import com.bluewhale.crm.commerce.domain.repository.ArSnapshotRepository;
import com.bluewhale.crm.commerce.infrastructure.persistence.entity.ArSnapshotEntity;
import com.bluewhale.crm.commerce.infrastructure.persistence.mapper.ArSnapshotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

/**
 * 客户应收快照仓储实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class ArSnapshotRepositoryImpl implements ArSnapshotRepository {

    private final ArSnapshotMapper arSnapshotMapper;

    @Override
    public List<ArSnapshot> page(ArSnapshotQuery query) {
        Page<ArSnapshotEntity> page = new Page<>(query.getCurrent(), query.getSize());
        return arSnapshotMapper.selectPage(page, pageWrapper(query))
                .getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(ArSnapshotQuery query) {
        return arSnapshotMapper.selectCount(pageWrapper(query));
    }

    @Override
    public ArSnapshot findByCustomerAndDate(Long customerId, LocalDate snapshotDate, String currency) {
        ArSnapshotEntity entity = arSnapshotMapper.selectOne(new LambdaQueryWrapper<ArSnapshotEntity>()
                .eq(ArSnapshotEntity::getCustomerId, customerId)
                .eq(ArSnapshotEntity::getSnapshotDate, snapshotDate)
                .eq(ArSnapshotEntity::getCurrency, currency)
                .last("limit 1"));
        return entity == null ? null : toDomain(entity);
    }

    @Override
    public void upsert(ArSnapshot snapshot) {
        ArSnapshot existing = findByCustomerAndDate(snapshot.getCustomerId(), snapshot.getSnapshotDate(),
                snapshot.getCurrency());
        if (existing == null) {
            ArSnapshotEntity entity = toEntity(snapshot);
            arSnapshotMapper.insert(entity);
            snapshot.setId(entity.getId());
            return;
        }
        snapshot.setId(existing.getId());
        arSnapshotMapper.updateById(toEntity(snapshot));
    }

    /**
     * 构造分页查询条件。
     *
     * @param query 查询条件
     * @return 查询包装器
     */
    private LambdaQueryWrapper<ArSnapshotEntity> pageWrapper(ArSnapshotQuery query) {
        LambdaQueryWrapper<ArSnapshotEntity> wrapper = new LambdaQueryWrapper<ArSnapshotEntity>()
                .eq(query.getCustomerId() != null, ArSnapshotEntity::getCustomerId, query.getCustomerId())
                .eq(query.getSnapshotDate() != null, ArSnapshotEntity::getSnapshotDate, query.getSnapshotDate())
                .eq(query.getOwnerId() != null, ArSnapshotEntity::getOwnerId, query.getOwnerId())
                .orderByDesc(ArSnapshotEntity::getSnapshotDate)
                .orderByDesc(ArSnapshotEntity::getUpdateTime);
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(ArSnapshotEntity::getCustomerCode, keyword)
                    .or().like(ArSnapshotEntity::getCustomerName, keyword));
        }
        return wrapper;
    }

    /**
     * 实体转领域对象。
     *
     * @param entity 实体
     * @return 快照
     */
    private ArSnapshot toDomain(ArSnapshotEntity entity) {
        ArSnapshot snapshot = new ArSnapshot();
        snapshot.setId(entity.getId());
        snapshot.setTenantId(entity.getTenantId());
        snapshot.setCustomerId(entity.getCustomerId());
        snapshot.setCustomerCode(entity.getCustomerCode());
        snapshot.setCustomerName(entity.getCustomerName());
        snapshot.setSnapshotDate(entity.getSnapshotDate());
        snapshot.setCurrency(entity.getCurrency());
        snapshot.setReceivableCount(entity.getReceivableCount());
        snapshot.setTotalRemaining(entity.getTotalRemaining());
        snapshot.setNotDue(entity.getNotDue());
        snapshot.setOverdue1To30(entity.getOverdue1To30());
        snapshot.setOverdue31To60(entity.getOverdue31To60());
        snapshot.setOverdue61To90(entity.getOverdue61To90());
        snapshot.setOverdueOver90(entity.getOverdueOver90());
        snapshot.setSource(entity.getSource());
        snapshot.setOwnerId(entity.getOwnerId());
        snapshot.setDeptId(entity.getDeptId());
        snapshot.setLastSyncAt(entity.getLastSyncAt());
        snapshot.setRemark(entity.getRemark());
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
    private ArSnapshotEntity toEntity(ArSnapshot snapshot) {
        ArSnapshotEntity entity = new ArSnapshotEntity();
        entity.setId(snapshot.getId());
        entity.setTenantId(snapshot.getTenantId());
        entity.setCustomerId(snapshot.getCustomerId());
        entity.setCustomerCode(snapshot.getCustomerCode());
        entity.setCustomerName(snapshot.getCustomerName());
        entity.setSnapshotDate(snapshot.getSnapshotDate());
        entity.setCurrency(snapshot.getCurrency());
        entity.setReceivableCount(snapshot.getReceivableCount());
        entity.setTotalRemaining(snapshot.getTotalRemaining());
        entity.setNotDue(snapshot.getNotDue());
        entity.setOverdue1To30(snapshot.getOverdue1To30());
        entity.setOverdue31To60(snapshot.getOverdue31To60());
        entity.setOverdue61To90(snapshot.getOverdue61To90());
        entity.setOverdueOver90(snapshot.getOverdueOver90());
        entity.setSource(snapshot.getSource());
        entity.setOwnerId(snapshot.getOwnerId());
        entity.setDeptId(snapshot.getDeptId());
        entity.setLastSyncAt(snapshot.getLastSyncAt());
        entity.setRemark(snapshot.getRemark());
        return entity;
    }
}
