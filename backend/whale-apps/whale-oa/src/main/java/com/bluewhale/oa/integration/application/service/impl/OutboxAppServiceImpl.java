package com.bluewhale.oa.integration.application.service.impl;

import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.integration.application.command.OutboxAppendCommand;
import com.bluewhale.oa.integration.application.service.OutboxAppService;
import com.bluewhale.oa.integration.application.view.OutboxEventView;
import com.bluewhale.oa.integration.domain.model.OutboxEvent;
import com.bluewhale.oa.integration.domain.model.OutboxStatus;
import com.bluewhale.oa.integration.domain.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 集成事件 Outbox 应用服务实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class OutboxAppServiceImpl implements OutboxAppService {

    /** 单次调度最大条数 */
    private static final int MAX_BATCH = 100;

    private final OutboxRepository outboxRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean append(OutboxAppendCommand command) {
        OutboxEvent event = new OutboxEvent();
        event.setEventKey(command.getEventKey());
        event.setEventType(command.getEventType());
        event.setAggregateType(command.getAggregateType());
        event.setAggregateId(command.getAggregateId());
        event.setPayload(command.getPayload());
        event.setStatus(OutboxStatus.PENDING);
        event.setRetryCount(0);
        return outboxRepository.insertIfAbsent(event);
    }

    @Override
    public List<OutboxEventView> listRetryable() {
        return outboxRepository.listRetryable(MAX_BATCH).stream().map(this::toView).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markSent(Long id) {
        OutboxEvent event = requireEvent(id);
        event.markSent();
        outboxRepository.update(event);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markFailed(Long id, String error) {
        OutboxEvent event = requireEvent(id);
        event.markFailed(error);
        outboxRepository.update(event);
    }

    /**
     * 查询事件，缺失时抛业务异常。
     *
     * @param id 事件ID
     * @return 事件
     */
    private OutboxEvent requireEvent(Long id) {
        return outboxRepository.findById(id)
                .orElseThrow(() -> OaErrorCode.exception(OaErrorCode.OUTBOX_EVENT_NOT_FOUND));
    }

    /**
     * 领域模型转视图。
     *
     * @param event 事件
     * @return 事件视图
     */
    private OutboxEventView toView(OutboxEvent event) {
        OutboxEventView view = new OutboxEventView();
        view.setId(event.getId());
        view.setEventKey(event.getEventKey());
        view.setEventType(event.getEventType());
        view.setAggregateType(event.getAggregateType());
        view.setAggregateId(event.getAggregateId());
        view.setStatus(event.getStatus() == null ? null : event.getStatus().name());
        view.setRetryCount(event.getRetryCount());
        view.setNextRetryTime(event.getNextRetryTime());
        view.setSentAt(event.getSentAt());
        view.setErrorMsg(event.getErrorMsg());
        return view;
    }
}
