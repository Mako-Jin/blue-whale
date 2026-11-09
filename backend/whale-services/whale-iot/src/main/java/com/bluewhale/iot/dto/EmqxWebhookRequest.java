package com.bluewhale.iot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * EMQX Webhook 事件请求体（client.connected / client.disconnected 等事件参数子集）。
 *
 * @author BlueWhale
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EmqxWebhookRequest {

    /** 事件名：client.connected / client.disconnected（其余事件忽略） */
    private String event;

    /** 客户端ID */
    private String clientId;

    /** 用户名：{productCode}/{deviceName}（与认证约定一致） */
    private String username;

    /** 连接节点名（记录用） */
    private String node;
}
