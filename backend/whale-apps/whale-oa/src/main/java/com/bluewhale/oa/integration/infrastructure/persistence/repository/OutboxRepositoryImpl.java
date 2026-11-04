package com.bluewhale.oa.integration.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.oa.integration.domain.model.OutboxEvent;
import com.bluewhale.oa.integration.domain.model.OutboxStatus;
import com.bluewhale.oa.integration.domain.repository.OutboxRepository;
import com.bluewhale.oa.integration.infrastructure.persistence.entity.OutboxEventEntity;
import com.bluewhale.oa.integration.infrastructure.persistence.mapper.OutboxEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 集成事件 Outbox 仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class OutboxRepositoryImpl implements OutboxRepository {

    private final OutboxEventMapper outboxEventMapper;

    @Override
    public boolean insertIfAbsent(OutboxEvent event) {
        if (findByEventKey(event.getEventKey()).isPresent()) {
            return false;
        }
        try {
            OutboxEventEntity entity = new OutboxEventEntity();
            fill(entity, event);
            outboxEventMapper.insert(entity);
            event.setId(entity.getId());
            return true;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    @Override
    public Optional<OutboxEvent> findById(Long id) {
        return Optional.ofNullable(outboxEventMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<OutboxEvent> findByEventKey(String eventKey) {
        return Optional.ofNullable(outboxEventMapper.selectOne(new LambdaQueryWrapper<OutboxEventEntity>()
                .eq(OutboxEventEntity::getEventKey, eventKey)
                .last("limit 1"))).map(this::toDomain);
    }

    @Override
    public List<OutboxEvent> listRetryable(int limit) {
        LocalDateTime now = LocalDateTime.now();
        return outboxEventMapper.selectList(new LambdaQueryWrapper<OutboxEventEntity>()
                        .in(OutboxEventEntity::getStatus, OutboxStatus.PENDING.name(), OutboxStatus.FAILED.name())
                        .and(wrapper -> wrapper.isNull(OutboxEventEntity::getNextRetryTime)
                                .or().le(OutboxEventEntity::getNextRetryTime, now))
                        .orderByAsc(OutboxEventEntity::getCreateTime)
                        .last("limit " + Math.max(limit, 1)))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public void update(OutboxEvent event) {
        OutboxEventEntity entity = new OutboxEventEntity();
        entity.setId(event.getId());
        entity.setStatus(event.getStatus() == null ? null : event.getStatus().name());
        entity.setRetryCount(event.getRetryCount());
        entity.setNextRetryTime(event.getNextRetryTime());
        entity.setSentAt(event.getSentAt());
        entity.setErrorMsg(event.getErrorMsg());
        outboxEventMapper.updateById(entity);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private OutboxEvent toDomain(OutboxEventEntity entity) {
        OutboxEvent event = new OutboxEvent();
        event.setId(entity.getId());
        event.setEventKey(entity.getEventKey());
        event.setEventType(entity.getEventType());
        event.setAggregateType(entity.getAggregateType());
        event.setAggregateId(entity.getAggregateId());
        event.setPayload(entity.getPayload());
        event.setStatus(entity.getStatus() == null ? OutboxStatus.PENDING : OutboxStatus.valueOf(entity.getStatus()));
        event.setRetryCount(entity.getRetryCount() == null ? 0 : entity.getRetryCount());
        event.setNextRetryTime(entity.getNextRetryTime());
        event.setSentAt(entity.getSentAt());
        event.setErrorMsg(entity.getErrorMsg());
        event.setCreateTime(entity.getCreateTime());
        return event;
    }

    /**
     * 领域模型写入实体。
     *
     * @param entity 实体
     * @param event 领域模型
     */
    private void fill(OutboxEventEntity entity, OutboxEvent event) {
        entity.setEventKey(event.getEventKey());
        entity.setEventType(event.getEventType());
        entity.setAggregateType(event.getAggregateType());
        entity.setAggregateId(event.getAggregateId());
        entity.setPayload(event.getPayload());
        entity.setStatus(event.getStatus() == null ? OutboxStatus.PENDING.name() : event.getStatus().name());
        entity.setRetryCount(event.getRetryCount());
        entity.setNextRetryTime(event.getNextRetryTime());
        entity.setSentAt(event.getSentAt());
        entity.setErrorMsg(event.getErrorMsg());
    }
}
