package com.bluewhale.audit.config;

import com.bluewhale.audit.mq.service.AuditLogMqConsumerService;
import com.bluewhale.audit.mq.support.AuditLogMqConsumer;
import com.bluewhale.common.audit.properties.AuditMqProperties;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 审计 MQ 消费端配置（AUTH-10）：声明交换机、业务队列、死信拓扑并装配监听器。
 *
 * <p>仅在 {@code whale.audit.mq.consume-enabled=true} 时生效，默认关闭保证未部署 RabbitMQ
 * 的环境行为不变。拓扑全部为 durable：交换机/队列/消息均持久化，Broker 重启不丢消息。</p>
 *
 * <p>死信方案：业务队列参数 {@code x-dead-letter-exchange}/{@code x-dead-letter-routing-key}
 * 指向死信交换机；监听器抛出的不可重试异常（毒消息）与重试耗尽后的异常均由容器 reject
 * 且不重新入队（{@code default-requeue-rejected=false}），消息进入死信队列供人工重放。</p>
 *
 * <p>生产者只投递不声明拓扑，避免多服务重复声明同一队列产生参数冲突；拓扑由本服务统一持有。</p>
 *
 * @author BlueWhale
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({RabbitTemplate.class, RabbitAutoConfiguration.class})
@ConditionalOnProperty(prefix = "whale.audit.mq", name = "consume-enabled", havingValue = "true")
@EnableConfigurationProperties(AuditMqProperties.class)
public class AuditMqConsumerConfiguration {

    /**
     * 审计业务交换机（direct，持久化）。
     *
     * @param properties MQ 汇聚配置
     * @return 审计交换机
     */
    @Bean
    public DirectExchange auditLogExchange(AuditMqProperties properties) {
        return new DirectExchange(properties.getExchange(), true, false);
    }

    /**
     * 审计业务队列（durable，绑定死信交换机）。
     *
     * @param properties MQ 汇聚配置
     * @return 审计队列
     */
    @Bean
    public Queue auditLogQueue(AuditMqProperties properties) {
        return QueueBuilder.durable(properties.getQueue())
                .deadLetterExchange(properties.getDeadLetterExchange())
                .deadLetterRoutingKey(properties.getRoutingKey())
                .build();
    }

    /**
     * 审计业务队列与交换机的绑定。
     *
     * @param properties MQ 汇聚配置
     * @param auditLogQueue 审计队列
     * @param auditLogExchange 审计交换机
     * @return 路由键为 {@code whale.audit.mq.routing-key} 的绑定
     */
    @Bean
    public Binding auditLogBinding(AuditMqProperties properties, Queue auditLogQueue,
                                   DirectExchange auditLogExchange) {
        return BindingBuilder.bind(auditLogQueue).to(auditLogExchange).with(properties.getRoutingKey());
    }

    /**
     * 死信交换机（direct，持久化）。
     *
     * @param properties MQ 汇聚配置
     * @return 死信交换机
     */
    @Bean
    public DirectExchange auditDeadLetterExchange(AuditMqProperties properties) {
        return new DirectExchange(properties.getDeadLetterExchange(), true, false);
    }

    /**
     * 死信队列（durable）。
     *
     * @param properties MQ 汇聚配置
     * @return 死信队列
     */
    @Bean
    public Queue auditDeadLetterQueue(AuditMqProperties properties) {
        return QueueBuilder.durable(properties.getDeadLetterQueue()).build();
    }

    /**
     * 死信队列与死信交换机的绑定（路由键与业务队列一致，便于整体重放）。
     *
     * @param properties MQ 汇聚配置
     * @param auditDeadLetterQueue 死信队列
     * @param auditDeadLetterExchange 死信交换机
     * @return 死信绑定
     */
    @Bean
    public Binding auditDeadLetterBinding(AuditMqProperties properties, Queue auditDeadLetterQueue,
                                          DirectExchange auditDeadLetterExchange) {
        return BindingBuilder.bind(auditDeadLetterQueue).to(auditDeadLetterExchange)
                .with(properties.getRoutingKey());
    }

    /**
     * 审计 MQ 消费者（{@code @RabbitListener} 由 Spring AMQP 注解处理器注册）。
     *
     * @param consumerService 幂等消费服务
     * @return 审计 MQ 消费者
     */
    @Bean
    public AuditLogMqConsumer auditLogMqConsumer(AuditLogMqConsumerService consumerService) {
        return new AuditLogMqConsumer(consumerService);
    }
}
