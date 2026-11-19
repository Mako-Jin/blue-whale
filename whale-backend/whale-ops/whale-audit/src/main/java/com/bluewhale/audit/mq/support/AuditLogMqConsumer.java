package com.bluewhale.audit.mq.support;

import com.bluewhale.audit.mq.service.AuditLogMqConsumerService;
import com.bluewhale.common.audit.entity.AuditLogRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

/**
 * 审计 MQ 消费者：从 RabbitMQ 读取审计记录并落库（AUTH-10）。
 *
 * <p>消息体为单条 {@link AuditLogRecord} 的 JSON（生产者 {@code AuditLogMqPublisher} 投递），
 * 消息属性 {@code messageId} 为幂等键。异常处理约定：</p>
 * <ul>
 *   <li>非法 JSON / 消息体超过上限 / 缺失 {@code messageId}：视为毒消息，抛
 *       {@link AmqpRejectAndDontRequeueException} 拒绝且不重新入队，经死信交换机进 DLQ；</li>
 *   <li>去重表主键冲突：该消息已成功落库，记录日志后正常返回（消费成功）；</li>
 *   <li>其他落库异常：原样抛出，由监听容器按
 *       {@code spring.rabbitmq.listener.simple.retry.*} 重试，重试耗尽后进 DLQ。</li>
 * </ul>
 *
 * @author BlueWhale
 */
@Slf4j
@RequiredArgsConstructor
public class AuditLogMqConsumer {

    /** JSON 反序列化器（Jackson 3，线程安全，字段与 audit_log 一一对应） */
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    /** 消息体字节上限（1 MB）：审计记录为单条结构化数据，超限视为异常投递，直接进 DLQ 防内存放大 */
    private static final int MAX_BODY_BYTES = 1024 * 1024;

    private final AuditLogMqConsumerService consumerService;

    /**
     * 消费审计队列消息。
     *
     * @param message 原始 AMQP 消息（队列名由 {@code whale.audit.mq.queue} 配置）
     */
    @RabbitListener(queues = "${whale.audit.mq.queue:bluewhale.audit.queue}")
    public void onMessage(Message message) {
        String messageId = message.getMessageProperties().getMessageId();
        if (message.getBody().length > MAX_BODY_BYTES) {
            log.warn("审计 MQ 消息体超限，转入死信队列: messageId={}, bytes={}",
                    messageId, message.getBody().length);
            throw new AmqpRejectAndDontRequeueException("审计消息体超过上限");
        }
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        AuditLogRecord record;
        try {
            record = JSON_MAPPER.readValue(body, AuditLogRecord.class);
        } catch (Exception e) {
            log.warn("审计 MQ 消息反序列化失败，转入死信队列: messageId={}, reason={}", messageId, e.getMessage());
            throw new AmqpRejectAndDontRequeueException("审计消息格式非法", e);
        }
        if (!StringUtils.hasText(messageId)) {
            log.warn("审计 MQ 消息缺少 messageId，无法幂等落库，转入死信队列: traceId={}", record.getTraceId());
            throw new AmqpRejectAndDontRequeueException("审计消息缺少 messageId");
        }
        try {
            consumerService.consume(messageId, record);
        } catch (DuplicateKeyException e) {
            log.info("审计 MQ 消息重复消费，已跳过: messageId={}", messageId);
        }
    }
}
