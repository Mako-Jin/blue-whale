package com.bluewhale.common.audit.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 审计日志 MQ 汇聚配置（{@code whale.audit.mq.*}）。
 *
 * <p>AUTH-10：在既有「本地落库 / HTTP 转发」之外增加 RabbitMQ 通道。
 * 生产者（各业务服务）开启 {@code publish-enabled} 后优先投递 MQ，
 * 失败再回退 HTTP 转发与本地落库；消费者（whale-audit）开启 {@code consume-enabled}
 * 后声明队列、死信并消费落库。两侧共用同一组交换机/队列名，默认值即约定值。</p>
 *
 * @author BlueWhale
 */
@Data
@ConfigurationProperties(prefix = "whale.audit.mq")
public class AuditMqProperties {

    /** 生产者开关（默认关闭，开启后审计切面优先投递 MQ） */
    private boolean publishEnabled = false;

    /** 消费者开关（默认关闭，仅 whale-audit 开启） */
    private boolean consumeEnabled = false;

    /** 审计交换机名称（direct） */
    private String exchange = "bluewhale.audit.exchange";

    /** 审计消息路由键 */
    private String routingKey = "audit.log";

    /** 审计业务队列名称 */
    private String queue = "bluewhale.audit.queue";

    /** 死信交换机名称 */
    private String deadLetterExchange = "bluewhale.audit.dlx";

    /** 死信队列名称 */
    private String deadLetterQueue = "bluewhale.audit.dlq";
}
