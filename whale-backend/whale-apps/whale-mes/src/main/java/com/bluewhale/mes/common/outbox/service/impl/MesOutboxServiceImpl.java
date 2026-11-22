package com.bluewhale.mes.common.outbox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.mes.common.outbox.entity.MesOutboxEvent;
import com.bluewhale.mes.common.outbox.mapper.MesOutboxEventMapper;
import com.bluewhale.mes.common.outbox.service.MesOutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * MES 集成发件箱服务实现
 *
 * <p>只负责落库与状态流转，实际投递（Feign/HTTP 调用与退避重试）由 M1 的
 * 定时任务消费 {@code PENDING / FAILED} 记录完成。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MesOutboxServiceImpl implements MesOutboxService {

    /** 待投递 */
    private static final String STATUS_PENDING = "PENDING";

    /** 已投递 */
    private static final String STATUS_SENT = "SENT";

    /** 投递失败 */
    private static final String STATUS_FAILED = "FAILED";

    private final MesOutboxEventMapper mesOutboxEventMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enqueue(String sourceEventId, String eventType, String aggregateType,
                        String aggregateId, String payload) {
        if (!StringUtils.hasText(sourceEventId) || !StringUtils.hasText(eventType)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.common.outbox-event-idempotency-key-event-type", "发件箱事件幂等键与事件类型不能为空");
        }
        LambdaQueryWrapper<MesOutboxEvent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MesOutboxEvent::getSourceEventId, sourceEventId);
        if (mesOutboxEventMapper.selectCount(wrapper) > 0) {
            log.debug("发件箱事件已存在，跳过写入: sourceEventId={}", sourceEventId);
            return;
        }
        MesOutboxEvent event = new MesOutboxEvent();
        event.setSourceEventId(sourceEventId);
        event.setEventType(eventType);
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setPayload(payload);
        event.setStatus(STATUS_PENDING);
        event.setRetryCount(0);
        mesOutboxEventMapper.insert(event);
        log.info("发件箱事件已入队: id={}, eventType={}", event.getId(), eventType);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markSent(Long id) {
        MesOutboxEvent event = requireEvent(id);
        event.setStatus(STATUS_SENT);
        event.setLastError(null);
        event.setNextRetryTime(null);
        mesOutboxEventMapper.updateById(event);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markFailed(Long id, int retryCount, LocalDateTime nextRetryTime, String error) {
        MesOutboxEvent event = requireEvent(id);
        event.setStatus(STATUS_FAILED);
        event.setRetryCount(retryCount);
        event.setNextRetryTime(nextRetryTime);
        event.setLastError(error);
        mesOutboxEventMapper.updateById(event);
    }

    /**
     * 按主键查询发件箱记录，不存在时抛 404。
     *
     * @param id 发件箱记录主键
     * @return 发件箱记录
     */
    private MesOutboxEvent requireEvent(Long id) {
        MesOutboxEvent event = id == null ? null : mesOutboxEventMapper.selectById(id);
        if (event == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.mes.common.outbox-event-found", "发件箱事件不存在：{0}", id);
        }
        return event;
    }
}
