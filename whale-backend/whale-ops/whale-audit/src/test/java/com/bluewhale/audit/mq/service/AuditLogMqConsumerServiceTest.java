package com.bluewhale.audit.mq.service;

import com.bluewhale.audit.mq.entity.AuditMqMessage;
import com.bluewhale.audit.mq.mapper.AuditMqMessageMapper;
import com.bluewhale.audit.service.AuditLogService;
import com.bluewhale.common.audit.entity.AuditLogRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 审计 MQ 消费服务单测：正常落库、重复消息主键冲突、落库异常向上抛出。
 *
 * @author BlueWhale
 */
@SuppressWarnings("unchecked")
class AuditLogMqConsumerServiceTest {

    private static final String MESSAGE_ID = "4f2c1a3b5d6e7f8091a2b3c4d5e6f708";

    private final AuditMqMessageMapper auditMqMessageMapper = mock(AuditMqMessageMapper.class);

    private final AuditLogService auditLogService = mock(AuditLogService.class);

    private final AuditLogMqConsumerService service =
            new AuditLogMqConsumerService(auditMqMessageMapper, auditLogService);

    @Test
    void shouldInsertDedupRowThenIngestRecord() {
        AuditLogRecord record = sampleRecord();

        service.consume(MESSAGE_ID, record);

        ArgumentCaptor<AuditMqMessage> messageCaptor = ArgumentCaptor.forClass(AuditMqMessage.class);
        verify(auditMqMessageMapper).insert(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getMessageId()).isEqualTo(MESSAGE_ID);
        assertThat(messageCaptor.getValue().getCreateTime()).isNotNull();
        verify(auditLogService).ingest(List.of(record));
    }

    @Test
    void shouldPropagateDuplicateKeyAndSkipIngest() {
        doThrow(new DuplicateKeyException("duplicate message id"))
                .when(auditMqMessageMapper).insert(any(AuditMqMessage.class));

        assertThatThrownBy(() -> service.consume(MESSAGE_ID, sampleRecord()))
                .isInstanceOf(DuplicateKeyException.class);
        verify(auditLogService, never()).ingest(any());
    }

    @Test
    void shouldPropagateIngestFailureForContainerRetry() {
        doThrow(new IllegalStateException("database unavailable")).when(auditLogService).ingest(any());

        assertThatThrownBy(() -> service.consume(MESSAGE_ID, sampleRecord()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("database unavailable");
    }

    private static AuditLogRecord sampleRecord() {
        AuditLogRecord record = new AuditLogRecord();
        record.setTraceId("trace-mq-1");
        record.setModule("whale-auth");
        record.setOperation("登录");
        record.setOperateType("LOGIN");
        record.setBizStatus(1);
        return record;
    }
}
