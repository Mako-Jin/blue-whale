package com.bluewhale.common.audit.support;

import com.bluewhale.common.audit.entity.AuditLogRecord;
import com.bluewhale.common.audit.properties.AuditMqProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 审计 MQ 发布器单测：消息属性、投递失败降级与序列化失败兜底。
 *
 * @author BlueWhale
 */
class AuditLogMqPublisherTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);

    private final AuditMqProperties properties = new AuditMqProperties();

    private final AuditLogMqPublisher publisher =
            new AuditLogMqPublisher(rabbitTemplate, JsonMapper.builder().build(), properties);

    @Test
    void shouldPublishPersistentJsonMessageWithMessageId() {
        AuditLogRecord record = new AuditLogRecord();
        record.setTraceId("trace-1");
        record.setOperation("创建用户");
        record.setModule("whale-auth");

        assertThat(publisher.publish(record)).isTrue();

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<MessagePostProcessor> processorCaptor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitTemplate).convertAndSend(eq(properties.getExchange()), eq(properties.getRoutingKey()),
                payloadCaptor.capture(), processorCaptor.capture());
        assertThat((String) payloadCaptor.getValue()).contains("\"operation\":\"创建用户\"");

        Message message = new Message("{}".getBytes(StandardCharsets.UTF_8), new MessageProperties());
        Message processed = processorCaptor.getValue().postProcessMessage(message);
        MessageProperties messageProperties = processed.getMessageProperties();
        assertThat(messageProperties.getMessageId()).hasSize(32).doesNotContain("-");
        assertThat(messageProperties.getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(messageProperties.getContentType()).isEqualTo("application/json");
    }

    @Test
    void shouldReturnFalseWhenBrokerUnavailable() {
        doThrow(new AmqpConnectException(new IllegalStateException("connection refused")))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(), any(MessagePostProcessor.class));

        assertThat(publisher.publish(new AuditLogRecord())).isFalse();
    }

    @Test
    void shouldReturnFalseWhenSerializationFails() {
        tools.jackson.databind.ObjectMapper brokenMapper = mock(tools.jackson.databind.ObjectMapper.class);
        org.mockito.Mockito.when(brokenMapper.writeValueAsString(any()))
                .thenThrow(new RuntimeException("boom"));
        AuditLogMqPublisher broken = new AuditLogMqPublisher(rabbitTemplate, brokenMapper, properties);

        assertThat(broken.publish(new AuditLogRecord())).isFalse();
    }
}
