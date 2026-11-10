package com.bluewhale.crm.commerce.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bluewhale.crm.commerce.domain.model.OrderOutboxEvent;
import com.bluewhale.crm.commerce.domain.repository.OrderOutboxRepository;
import com.bluewhale.crm.commerce.infrastructure.persistence.entity.OrderOutboxEntity;
import com.bluewhale.crm.commerce.infrastructure.persistence.mapper.OrderOutboxMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商务订单下发 Outbox 仓储实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class OrderOutboxRepositoryImpl implements OrderOutboxRepository {

    /** 聚合类型 */
    private static final String AGGREGATE_TYPE = "crm_sales_order";

    /** 事件类型 */
    private static final String EVENT_TYPE = "SALES_ORDER_PUSH";

    /** 状态：待发送 */
    private static final String STATUS_PENDING = "PENDING";

    /** 状态：已发送 */
    private static final String STATUS_SENT = "SENT";

    /** 状态：失败待重试 */
    private static final String STATUS_FAILED = "FAILED";

    /** 失败原因长度上限 */
    private static final int ERROR_MAX_LENGTH = 512;

    private final OrderOutboxMapper orderOutboxMapper;

    @Override
    public void enqueue(Long orderId, String payload, LocalDateTime nextRetryAt) {
        OrderOutboxEntity entity = new OrderOutboxEntity();
        entity.setAggregateType(AGGREGATE_TYPE);
        entity.setAggregateId(orderId);
        entity.setEventType(EVENT_TYPE);
        entity.setPayload(payload);
        entity.setStatus(STATUS_PENDING);
        entity.setRetryCount(0);
        entity.setNextRetryAt(nextRetryAt);
        orderOutboxMapper.insert(entity);
    }

    @Override
    public List<OrderOutboxEvent> findPending(int limit, LocalDateTime now) {
        return orderOutboxMapper.selectList(new LambdaQueryWrapper<OrderOutboxEntity>()
                        .eq(OrderOutboxEntity::getAggregateType, AGGREGATE_TYPE)
                        .eq(OrderOutboxEntity::getEventType, EVENT_TYPE)
                        .and(w -> w.eq(OrderOutboxEntity::getStatus, STATUS_PENDING)
                                .or().eq(OrderOutboxEntity::getStatus, STATUS_FAILED))
                        .le(OrderOutboxEntity::getNextRetryAt, now)
                        .orderByAsc(OrderOutboxEntity::getNextRetryAt)
                        .last("limit " + Math.max(1, limit)))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public boolean markSent(Long id) {
        return orderOutboxMapper.update(null, new LambdaUpdateWrapper<OrderOutboxEntity>()
                .eq(OrderOutboxEntity::getId, id)
                .set(OrderOutboxEntity::getStatus, STATUS_SENT)
                .set(OrderOutboxEntity::getLastError, null)
                .set(OrderOutboxEntity::getNextRetryAt, null)) > 0;
    }

    @Override
    public boolean markFailed(Long id, String error, LocalDateTime nextRetryAt) {
        String message = error == null ? "下发 ERP 失败" : error.trim();
        String stored = message.length() > ERROR_MAX_LENGTH ? message.substring(0, ERROR_MAX_LENGTH) : message;
        return orderOutboxMapper.update(null, new LambdaUpdateWrapper<OrderOutboxEntity>()
                .eq(OrderOutboxEntity::getId, id)
                .set(OrderOutboxEntity::getStatus, STATUS_FAILED)
                .set(OrderOutboxEntity::getLastError, stored)
                .set(OrderOutboxEntity::getNextRetryAt, nextRetryAt)
                .setSql("retry_count = retry_count + 1")) > 0;
    }

    @Override
    public void resetForRetry(Long orderId, LocalDateTime nextRetryAt) {
        orderOutboxMapper.update(null, new LambdaUpdateWrapper<OrderOutboxEntity>()
                .eq(OrderOutboxEntity::getAggregateType, AGGREGATE_TYPE)
                .eq(OrderOutboxEntity::getEventType, EVENT_TYPE)
                .eq(OrderOutboxEntity::getAggregateId, orderId)
                .set(OrderOutboxEntity::getStatus, STATUS_PENDING)
                .set(OrderOutboxEntity::getNextRetryAt, nextRetryAt)
                .set(OrderOutboxEntity::getLastError, null));
    }

    @Override
    public void cancelByOrderId(Long orderId) {
        orderOutboxMapper.update(null, new LambdaUpdateWrapper<OrderOutboxEntity>()
                .eq(OrderOutboxEntity::getAggregateType, AGGREGATE_TYPE)
                .eq(OrderOutboxEntity::getEventType, EVENT_TYPE)
                .eq(OrderOutboxEntity::getAggregateId, orderId)
                .ne(OrderOutboxEntity::getStatus, STATUS_SENT)
                .set(OrderOutboxEntity::getStatus, STATUS_SENT)
                .set(OrderOutboxEntity::getNextRetryAt, null));
    }

    /**
     * 实体转领域对象。
     *
     * @param entity 实体
     * @return 事件
     */
    private OrderOutboxEvent toDomain(OrderOutboxEntity entity) {
        OrderOutboxEvent event = new OrderOutboxEvent();
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
