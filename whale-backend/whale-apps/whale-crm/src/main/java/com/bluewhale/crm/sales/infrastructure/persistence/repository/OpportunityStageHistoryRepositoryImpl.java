package com.bluewhale.crm.sales.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.crm.sales.domain.model.OpportunityStage;
import com.bluewhale.crm.sales.domain.model.OpportunityStageHistory;
import com.bluewhale.crm.sales.domain.repository.OpportunityStageHistoryRepository;
import com.bluewhale.crm.sales.infrastructure.persistence.entity.OpportunityStageHistoryEntity;
import com.bluewhale.crm.sales.infrastructure.persistence.mapper.OpportunityStageHistoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 商机阶段历史仓储实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class OpportunityStageHistoryRepositoryImpl implements OpportunityStageHistoryRepository {

    private final OpportunityStageHistoryMapper historyMapper;

    @Override
    public List<OpportunityStageHistory> listByOpportunity(Long opportunityId) {
        return historyMapper.selectList(new LambdaQueryWrapper<OpportunityStageHistoryEntity>()
                        .eq(OpportunityStageHistoryEntity::getOpportunityId, opportunityId)
                        .orderByAsc(OpportunityStageHistoryEntity::getChangedAt))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public void insert(OpportunityStageHistory history) {
        OpportunityStageHistoryEntity entity = new OpportunityStageHistoryEntity();
        entity.setOpportunityId(history.getOpportunityId());
        entity.setFromStage(history.getFromStage() == null ? null : history.getFromStage().name());
        entity.setToStage(history.getToStage() == null ? null : history.getToStage().name());
        entity.setAmount(history.getAmount());
        entity.setProbability(history.getProbability());
        entity.setLostReason(history.getLostReason());
        entity.setOperatorId(history.getOperatorId());
        entity.setChangedAt(history.getChangedAt());
        historyMapper.insert(entity);
        history.setId(entity.getId());
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private OpportunityStageHistory toDomain(OpportunityStageHistoryEntity entity) {
        OpportunityStageHistory history = new OpportunityStageHistory();
        history.setId(entity.getId());
        history.setOpportunityId(entity.getOpportunityId());
        history.setFromStage(entity.getFromStage() == null ? null : OpportunityStage.from(entity.getFromStage()));
        history.setToStage(OpportunityStage.from(entity.getToStage()));
        history.setAmount(entity.getAmount());
        history.setProbability(entity.getProbability());
        history.setLostReason(entity.getLostReason());
        history.setOperatorId(entity.getOperatorId());
        history.setChangedAt(entity.getChangedAt());
        return history;
    }
}
