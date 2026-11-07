package com.bluewhale.crm.commerce.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.crm.commerce.domain.model.CollectionAction;
import com.bluewhale.crm.commerce.domain.repository.CollectionActionRepository;
import com.bluewhale.crm.commerce.infrastructure.persistence.entity.CollectionActionEntity;
import com.bluewhale.crm.commerce.infrastructure.persistence.mapper.CollectionActionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 催收记录仓储实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class CollectionActionRepositoryImpl implements CollectionActionRepository {

    private final CollectionActionMapper collectionActionMapper;

    @Override
    public void insert(CollectionAction action) {
        CollectionActionEntity entity = new CollectionActionEntity();
        entity.setPlanId(action.getPlanId());
        entity.setContractId(action.getContractId());
        entity.setActionType(action.getActionType());
        entity.setContent(action.getContent());
        entity.setPromiseDate(action.getPromiseDate());
        entity.setPromiseAmount(action.getPromiseAmount());
        entity.setOperatorId(action.getOperatorId());
        collectionActionMapper.insert(entity);
        action.setId(entity.getId());
        action.setCreateTime(entity.getCreateTime());
    }

    @Override
    public List<CollectionAction> findByPlan(Long planId) {
        return collectionActionMapper.selectList(new LambdaQueryWrapper<CollectionActionEntity>()
                        .eq(CollectionActionEntity::getPlanId, planId)
                        .orderByDesc(CollectionActionEntity::getCreateTime))
                .stream().map(this::toDomain).toList();
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private CollectionAction toDomain(CollectionActionEntity entity) {
        CollectionAction action = new CollectionAction();
        action.setId(entity.getId());
        action.setPlanId(entity.getPlanId());
        action.setContractId(entity.getContractId());
        action.setActionType(entity.getActionType());
        action.setContent(entity.getContent());
        action.setPromiseDate(entity.getPromiseDate());
        action.setPromiseAmount(entity.getPromiseAmount());
        action.setOperatorId(entity.getOperatorId());
        action.setCreateTime(entity.getCreateTime());
        return action;
    }
}
