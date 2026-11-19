package com.bluewhale.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.message.config.MessageSendProperties;
import com.bluewhale.message.mapper.MessageRecordMapper;
import com.bluewhale.message.po.MessageRecordPo;
import com.bluewhale.message.service.MessageSendService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MessageDispatchRecovery} 补偿任务单元测试。
 *
 * @author BlueWhale
 */
class MessageDispatchRecoveryTest {

    private final MessageRecordMapper recordMapper = mock(MessageRecordMapper.class);

    private final MessageSendService messageSendService = mock(MessageSendService.class);

    private final MessageSendProperties properties = new MessageSendProperties();

    private final MessageDispatchRecovery recovery =
            new MessageDispatchRecovery(recordMapper, messageSendService, properties);

    @Test
    @DisplayName("回收抢占超时记录并按租户补偿未投递消息")
    void shouldRecoverStalePendingPerTenant() {
        when(recordMapper.recoverStaleDispatching(any(), any())).thenReturn(2);
        when(recordMapper.selectPendingTenantIds(any())).thenReturn(List.of(1L, 2L));
        MessageRecordPo record = new MessageRecordPo();
        record.setId(900L);
        record.setTraceId("trace-1");
        when(recordMapper.selectList(any(Wrapper.class))).thenReturn(List.of(record));

        recovery.recover();

        verify(recordMapper).recoverStaleDispatching(any(), any());
        verify(messageSendService).dispatchAsync(900L, 1L, "trace-1");
        verify(messageSendService).dispatchAsync(900L, 2L, "trace-1");
        assertNull(TenantContext.get());
    }
}
