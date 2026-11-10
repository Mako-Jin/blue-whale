package com.bluewhale.common.audit.config;

import com.bluewhale.common.audit.properties.AuditMqProperties;
import com.bluewhale.common.audit.support.AuditLogMqPublisher;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ObjectMapper;

/**
 * 审计 MQ 汇聚自动配置（AUTH-10，生产者侧）。
 *
 * <p>生效条件：类路径存在 Spring AMQP（使用方显式引入 {@code spring-boot-starter-amqp}）
 * 且 {@code whale.audit.mq.publish-enabled=true}。不满足条件时本配置不装配任何 Bean，
 * 审计切面按原「HTTP 转发 → 本地落库」链路运行，行为与引入 MQ 前完全一致。</p>
 *
 * <p>排序在 {@link RabbitAutoConfiguration} 之后，确保 {@link RabbitTemplate} 已创建；
 * 交换机与队列声明由消费端（whale-audit）负责，生产者只投递不声明拓扑，
 * 避免多服务重复声明同一队列产生参数冲突。</p>
 *
 * @author BlueWhale
 */
@AutoConfiguration(after = RabbitAutoConfiguration.class)
@ConditionalOnClass({RabbitTemplate.class, RabbitAutoConfiguration.class})
@ConditionalOnProperty(prefix = "whale.audit.mq", name = "publish-enabled", havingValue = "true")
@EnableConfigurationProperties(AuditMqProperties.class)
public class AuditMqAutoConfiguration {

    /**
     * 审计 MQ 发布器。
     *
     * @param rabbitTemplate Spring AMQP 模板
     * @param objectMapper Spring 管理的 Jackson 3 序列化器
     * @param properties MQ 汇聚配置
     * @return 审计 MQ 发布器
     */
    @Bean
    @ConditionalOnMissingBean
    public AuditLogMqPublisher auditLogMqPublisher(RabbitTemplate rabbitTemplate,
                                                   ObjectMapper objectMapper,
                                                   AuditMqProperties properties) {
        return new AuditLogMqPublisher(rabbitTemplate, objectMapper, properties);
    }
}
