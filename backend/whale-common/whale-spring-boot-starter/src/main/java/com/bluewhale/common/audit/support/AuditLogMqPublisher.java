package com.bluewhale.common.audit.support;

import com.bluewhale.common.audit.entity.AuditLogRecord;
import com.bluewhale.common.audit.properties.AuditMqProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/**
 * 审计日志 MQ 发布器：把审计记录投递到 RabbitMQ，由 whale-audit 消费落库（AUTH-10）。
 *
 * <p>仅在 {@code whale.audit.mq.publish-enabled=true} 且使用方引入 amqp starter 时装配。
 * 调用语义与 {@link AuditLogForwarder} 一致为「尽力而为」：投递异常、序列化失败等一律返回
 * {@code false} 并告警，由 {@link com.bluewhale.common.audit.aop.AuditLogAspect} 继续回退
 * HTTP 转发与本地落库，<b>绝不抛出异常</b>。</p>
 *
 * <p>消息属性：{@code messageId} 为 32 位无横线 UUID（消费端据此幂等去重）、
 * 投递模式 {@code PERSISTENT}（Broker 持久化队列 + 持久化消息）、
 * {@code contentType=application/json}；请求体为单条记录的 JSON。</p>
 *
 * @author BlueWhale
 */
@Slf4j
public class AuditLogMqPublisher {

    /** 消息内容类型 */
    private static final String CONTENT_TYPE_JSON = "application/json";

    private final RabbitTemplate rabbitTemplate;

    private final ObjectMapper objectMapper;

    private final AuditMqProperties properties;

    /**
     * 构造 MQ 发布器。
     *
     * @param rabbitTemplate Spring AMQP 模板（由 RabbitAutoConfiguration 提供）
     * @param objectMapper Spring 管理的 Jackson 3 序列化器（含 JavaTime 模块）
     * @param properties MQ 汇聚配置（交换机与路由键）
     */
    public AuditLogMqPublisher(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper,
                               AuditMqProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * 投递单条审计记录到 RabbitMQ。
     *
     * @param record 待投递的审计记录（参数与返回体已由切面脱敏）
     * @return {@code true} 表示已成功投递（不保证消费端已落库）；{@code false} 表示需回退后续通道
     */
    public boolean publish(AuditLogRecord record) {
        try {
            String payload = objectMapper.writeValueAsString(record);
            rabbitTemplate.convertAndSend(properties.getExchange(), properties.getRoutingKey(), payload,
                    message -> {
                        MessageProperties messageProperties = message.getMessageProperties();
                        messageProperties.setMessageId(UUID.randomUUID().toString().replace("-", ""));
                        messageProperties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        messageProperties.setContentType(CONTENT_TYPE_JSON);
                        return message;
                    });
            return true;
        } catch (Exception e) {
            log.warn("审计日志 MQ 投递失败，回退后续通道: traceId={}, operation={}, reason={}",
                    record.getTraceId(), record.getOperation(), e.getMessage());
            return false;
        }
    }
}
