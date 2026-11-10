package com.bluewhale.oa.integration.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.oa.integration.application.command.OutboxAppendCommand;
import com.bluewhale.oa.integration.domain.model.OutboxEvent;
import com.bluewhale.oa.integration.domain.model.OutboxStatus;
import com.bluewhale.oa.integration.domain.repository.OutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link OutboxAppServiceImpl} 事件幂等与状态流转单元测试。
 *
 * @author BlueWhale
 */
class OutboxAppServiceImplTest {

    private final OutboxRepository outboxRepository = mock(OutboxRepository.class);

    private final OutboxAppServiceImpl outboxAppService = new OutboxAppServiceImpl(outboxRepository);

    @Test
    @DisplayName("append：首次追加成功")
    void shouldAppendEvent() {
        when(outboxRepository.insertIfAbsent(any())).thenReturn(true);

        assertTrue(outboxAppService.append(appendCommand()));

        verify(outboxRepository).insertIfAbsent(any());
    }

    @Test
    @DisplayName("append：事件键重复时返回 false")
    void shouldIgnoreDuplicateEvent() {
        when(outboxRepository.insertIfAbsent(any())).thenReturn(false);

        assertEquals(false, outboxAppService.append(appendCommand()));
    }

    @Test
    @DisplayName("markSent：事件存在则落库为 SENT")
    void shouldMarkSent() {
        OutboxEvent event = new OutboxEvent();
        event.setId(1L);
        event.setStatus(OutboxStatus.PENDING);
        when(outboxRepository.findById(1L)).thenReturn(Optional.of(event));

        outboxAppService.markSent(1L);

        assertEquals(OutboxStatus.SENT, event.getStatus());
        verify(outboxRepository).update(event);
    }

    @Test
    @DisplayName("markSent：事件不存在返回 40406")
    void shouldRejectMissingEvent() {
        when(outboxRepository.findById(1L)).thenReturn(Optional.empty());

        BizException exception = assertThrows(BizException.class, () -> outboxAppService.markSent(1L));

        assertEquals(40406, exception.getCode());
    }

    /**
     * 构造追加命令。
     *
     * @return 追加命令
     */
    private static OutboxAppendCommand appendCommand() {
        OutboxAppendCommand command = new OutboxAppendCommand();
        command.setEventKey("LEAVE_APPROVED_1");
        command.setEventType("OA_APPROVAL_APPROVED");
        command.setAggregateType("APPROVAL_INSTANCE");
        command.setAggregateId("1");
        command.setPayload("{\"businessType\":\"LEAVE\"}");
        return command;
    }
}
