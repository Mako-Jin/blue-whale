package com.bluewhale.crm.commerce.infrastructure.persistence.repository;

import com.bluewhale.crm.commerce.domain.repository.OrderPushLogRepository;
import com.bluewhale.crm.commerce.infrastructure.persistence.entity.OrderPushLogEntity;
import com.bluewhale.crm.commerce.infrastructure.persistence.mapper.OrderPushLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 商务订单下发日志仓储实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class OrderPushLogRepositoryImpl implements OrderPushLogRepository {

    private final OrderPushLogMapper orderPushLogMapper;

    @Override
    public void record(Long orderId, Integer attemptNo, String requestRef, String responseRef,
                       String status, String errorMsg, long costMs) {
        OrderPushLogEntity entity = new OrderPushLogEntity();
        entity.setOrderId(orderId);
        entity.setAttemptNo(attemptNo);
        entity.setRequestRef(requestRef);
        entity.setResponseRef(responseRef);
        entity.setStatus(status);
        entity.setErrorMsg(errorMsg);
        entity.setCostMs(costMs);
        orderPushLogMapper.insert(entity);
    }
}
