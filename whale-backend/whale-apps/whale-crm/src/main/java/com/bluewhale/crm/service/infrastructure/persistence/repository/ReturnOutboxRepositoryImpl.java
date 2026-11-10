package com.bluewhale.crm.service.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bluewhale.crm.service.domain.model.ReturnOutboxEvent;
import com.bluewhale.crm.service.domain.repository.ReturnOutboxRepository;
import com.bluewhale.crm.service.infrastructure.persistence.entity.ReturnOutboxEntity;
import com.bluewhale.crm.service.infrastructure.persistence.mapper.ReturnOutboxMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 退换货下发 Outbox 仓储实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class ReturnOutboxRepositoryImpl implements ReturnOutboxRepository {

    /** 聚合类型 */
    private static final String AGGREGATE_TYPE = "crm_return_request";

    /** 事件类型 */
    private static final String EVENT_TYPE = "RETURN_REQUEST_EXECUTE";

    /** 状态：待发送 */
    private static final String STATUS_PENDING = "PENDING";

    /** 状态：已发送 */
    private static final String STATUS_SENT = "SENT";

    /** 状态：失败待重试 */
    private static final String STATUS_FAILED = "FAILED";

    /** 失败原因长度上限 */
    private static final int ERROR_MAX_LENGTH = 512;

    private final ReturnOutboxMapper returnOutboxMapper;

    @Override
    public void enqueue(Long requestId, String payload, LocalDateTime nextRetryAt) {
        ReturnOutboxEntity entity = new ReturnOutboxEntity();
        entity.setAggregateType(AGGREGATE_TYPE);
        entity.setAggregateId(requestId);
        entity.setEventType(EVENT_TYPE);
        entity.setPayload(payload);
        entity.setStatus(STATUS_PENDING);
        entity.setRetryCount(0);
        entity.setNextRetryAt(nextRetryAt);
        returnOutboxMapper.insert(entity);
    }

    @Override
    public List<ReturnOutboxEvent> findPending(int limit, LocalDateTime now) {
        return returnOutboxMapper.selectList(new LambdaQueryWrapper<ReturnOutboxEntity>()
                        .eq(ReturnOutboxEntity::getAggregateType, AGGREGATE_TYPE)
                        .eq(ReturnOutboxEntity::getEventType, EVENT_TYPE)
                        .and(w -> w.eq(ReturnOutboxEntity::getStatus, STATUS_PENDING)
                                .or().eq(ReturnOutboxEntity::getStatus, STATUS_FAILED))
                        .le(ReturnOutboxEntity::getNextRetryAt, now)
                        .orderByAsc(ReturnOutboxEntity::getNextRetryAt)
                        .last("limit " + Math.max(1, limit)))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public boolean markSent(Long id) {
        return returnOutboxMapper.update(null, new LambdaUpdateWrapper<ReturnOutboxEntity>()
                .eq(ReturnOutboxEntity::getId, id)
                .set(ReturnOutboxEntity::getStatus, STATUS_SENT)
                .set(ReturnOutboxEntity::getLastError, null)
                .set(ReturnOutboxEntity::getNextRetryAt, null)) > 0;
    }

    @Override
    public boolean markFailed(Long id, String error, LocalDateTime nextRetryAt) {
        String message = error == null ? "下发 ERP 退货失败" : error.trim();
        String stored = message.length() > ERROR_MAX_LENGTH ? message.substring(0, ERROR_MAX_LENGTH) : message;
        return returnOutboxMapper.update(null, new LambdaUpdateWrapper<ReturnOutboxEntity>()
                .eq(ReturnOutboxEntity::getId, id)
                .set(ReturnOutboxEntity::getStatus, STATUS_FAILED)
                .set(ReturnOutboxEntity::getLastError, stored)
                .set(ReturnOutboxEntity::getNextRetryAt, nextRetryAt)
                .setSql("retry_count = retry_count + 1")) > 0;
    }

    @Override
    public void resetForRetry(Long requestId, LocalDateTime nextRetryAt) {
        returnOutboxMapper.update(null, new LambdaUpdateWrapper<ReturnOutboxEntity>()
                .eq(ReturnOutboxEntity::getAggregateType, AGGREGATE_TYPE)
                .eq(ReturnOutboxEntity::getEventType, EVENT_TYPE)
                .eq(ReturnOutboxEntity::getAggregateId, requestId)
                .set(ReturnOutboxEntity::getStatus, STATUS_PENDING)
                .set(ReturnOutboxEntity::getNextRetryAt, nextRetryAt)
                .set(ReturnOutboxEntity::getLastError, null));
    }

    @Override
    public void cancelByRequestId(Long requestId) {
        returnOutboxMapper.update(null, new LambdaUpdateWrapper<ReturnOutboxEntity>()
                .eq(ReturnOutboxEntity::getAggregateType, AGGREGATE_TYPE)
                .eq(ReturnOutboxEntity::getEventType, EVENT_TYPE)
                .eq(ReturnOutboxEntity::getAggregateId, requestId)
                .ne(ReturnOutboxEntity::getStatus, STATUS_SENT)
                .set(ReturnOutboxEntity::getStatus, STATUS_SENT)
                .set(ReturnOutboxEntity::getNextRetryAt, null));
    }

    /**
     * 实体转领域对象。
     *
     * @param entity 实体
     * @return 事件
     */
    private ReturnOutboxEvent toDomain(ReturnOutboxEntity entity) {
        ReturnOutboxEvent event = new ReturnOutboxEvent();
        event.setId(entity.getId());
        event.setTenantId(entity.getTenantId());
        event.setAggregateId(entity.getAggregateId());
        event.setStatus(entity.getStatus());
        event.setRetryCount(entity.getRetryCount());
        event.setNextRetryAt(entity.getNextRetryAt());
        event.setLastError(entity.getLastError());
        return event;
    }
}
