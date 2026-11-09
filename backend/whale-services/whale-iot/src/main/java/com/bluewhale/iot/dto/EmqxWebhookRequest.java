package com.bluewhale.iot.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * EMQX Webhook 事件请求体（client.connected / client.disconnected / message.publish 参数子集）。
 *
 * <p>EMQX 5 会把事件参数平铺在顶层；字段名对齐其默认输出（{@code clientid} 而非
 * {@code clientId}），只取本服务用得到的子集，其余忽略。</p>
 *
 * @author BlueWhale
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EmqxWebhookRequest {

    /** 事件名：client.connected / client.disconnected / message.publish（其余事件忽略） */
    private String event;

    /** 客户端ID（EMQX 报文键为 {@code clientid}） */
    @JsonAlias("clientid")
    private String clientId;

    /** 用户名：{productCode}/{deviceName}（与认证约定一致） */
    private String username;

    /** 连接节点名（记录用） */
    private String node;

    /** 消息主题（message.publish 事件） */
    private String topic;

    /** 消息报文（message.publish 事件；Webhook 侧按纯文本编码时可直接解析为 JSON） */
    private String payload;

    /** 消息 QoS（message.publish 事件） */
    private Integer qos;

    /** 事件时间戳（毫秒） */
    private Long timestamp;
}
