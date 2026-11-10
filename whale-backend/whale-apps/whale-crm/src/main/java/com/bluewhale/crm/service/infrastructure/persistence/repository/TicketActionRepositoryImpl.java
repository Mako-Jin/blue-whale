package com.bluewhale.crm.service.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.crm.service.domain.model.TicketAction;
import com.bluewhale.crm.service.domain.model.TicketActionType;
import com.bluewhale.crm.service.domain.model.TicketStatus;
import com.bluewhale.crm.service.domain.repository.TicketActionRepository;
import com.bluewhale.crm.service.infrastructure.persistence.entity.TicketActionEntity;
import com.bluewhale.crm.service.infrastructure.persistence.mapper.TicketActionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 工单处理记录仓储实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class TicketActionRepositoryImpl implements TicketActionRepository {

    private final TicketActionMapper ticketActionMapper;

    @Override
    public List<TicketAction> findByTicketId(Long ticketId) {
        return ticketActionMapper.selectList(new LambdaQueryWrapper<TicketActionEntity>()
                        .eq(TicketActionEntity::getTicketId, ticketId)
                        .orderByAsc(TicketActionEntity::getCreatedAt)
                        .orderByAsc(TicketActionEntity::getId))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public void insert(TicketAction action) {
        TicketActionEntity entity = new TicketActionEntity();
        entity.setTenantId(action.getTenantId());
        entity.setTicketId(action.getTicketId());
        entity.setActionType(action.getActionType() == null ? null : action.getActionType().name());
        entity.setFromStatus(action.getFromStatus() == null ? null : action.getFromStatus().name());
        entity.setToStatus(action.getToStatus() == null ? null : action.getToStatus().name());
        entity.setOperatorId(action.getOperatorId());
        entity.setOperatorName(action.getOperatorName());
        entity.setContent(action.getContent());
        entity.setCreatedAt(action.getCreatedAt());
        ticketActionMapper.insert(entity);
        action.setId(entity.getId());
    }

    /**
     * 实体转领域对象。
     *
     * @param entity 实体
     * @return 处理记录
     */
    private TicketAction toDomain(TicketActionEntity entity) {
        TicketAction action = new TicketAction();
        action.setId(entity.getId());
        action.setTenantId(entity.getTenantId());
        action.setTicketId(entity.getTicketId());
        action.setActionType(entity.getActionType() == null
                ? null : TicketActionType.valueOf(entity.getActionType()));
        action.setFromStatus(entity.getFromStatus() == null
                ? null : TicketStatus.from(entity.getFromStatus()));
        action.setToStatus(entity.getToStatus() == null ? null : TicketStatus.from(entity.getToStatus()));
        action.setOperatorId(entity.getOperatorId());
        action.setOperatorName(entity.getOperatorName());
        action.setContent(entity.getContent());
        action.setCreatedAt(entity.getCreatedAt());
        return action;
    }
}
