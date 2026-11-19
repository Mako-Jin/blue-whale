package com.bluewhale.audit.mq.support;

import com.bluewhale.audit.mq.service.AuditLogMqConsumerService;
import com.bluewhale.common.audit.entity.AuditLogRecord;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.dao.DuplicateKeyException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 审计 MQ 消费者单测：正常解析落库、毒消息拒绝、缺失消息ID、重复消息跳过。
 *
 * @author BlueWhale
 */
class AuditLogMqConsumerTest {

    private static final String MESSAGE_ID = "4f2c1a3b5d6e7f8091a2b3c4d5e6f708";

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final AuditLogMqConsumerService consumerService = mock(AuditLogMqConsumerService.class);

    private final AuditLogMqConsumer consumer = new AuditLogMqConsumer(consumerService);

    @Test
    void shouldParseMessageAndDelegateToConsumerService() {
        AuditLogRecord record = sampleRecord();
        Message message = message(JSON_MAPPER.writeValueAsString(record), MESSAGE_ID);

        consumer.onMessage(message);

        verify(consumerService).consume(eq(MESSAGE_ID), any(AuditLogRecord.class));
    }

    @Test
    void shouldRejectPoisonMessageWithInvalidJson() {
        Message message = message("{not-json", MESSAGE_ID);

        assertThatThrownBy(() -> consumer.onMessage(message))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verify(consumerService, never()).consume(any(), any());
    }

    @Test
    void shouldRejectMessageWithoutMessageId() {
        Message message = message(JSON_MAPPER.writeValueAsString(sampleRecord()), null);

        assertThatThrownBy(() -> consumer.onMessage(message))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verify(consumerService, never()).consume(any(), any());
    }

    @Test
    void shouldRejectOversizedBodyBeforeDeserialization() {
        byte[] oversized = new byte[1024 * 1024 + 1];
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(MESSAGE_ID);
        Message message = new Message(oversized, properties);

        assertThatThrownBy(() -> consumer.onMessage(message))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verify(consumerService, never()).consume(any(), any());
    }

    @Test
    void shouldSwallowDuplicateKeyException() {
        Message message = message(JSON_MAPPER.writeValueAsString(sampleRecord()), MESSAGE_ID);
        doThrow(new DuplicateKeyException("duplicate")).when(consumerService).consume(any(), any());

        assertThatCode(() -> consumer.onMessage(message)).doesNotThrowAnyException();
    }

    private static Message message(String body, String messageId) {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(messageId);
        return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
    }

    private static AuditLogRecord sampleRecord() {
        AuditLogRecord record = new AuditLogRecord();
        record.setTraceId("trace-mq-1");
        record.setUserId(1001L);
        record.setUsername("alice");
        record.setModule("whale-auth");
        record.setOperation("登录");
        record.setOperateType("LOGIN");
        record.setBizStatus(1);
        record.setCostMs(12L);
        record.setCreateTime(LocalDateTime.of(2026, 10, 23, 10, 30, 0));
        return record;
    }
}
