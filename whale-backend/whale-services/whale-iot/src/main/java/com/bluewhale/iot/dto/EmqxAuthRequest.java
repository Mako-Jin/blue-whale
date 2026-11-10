package com.bluewhale.iot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * EMQX HTTP 认证请求体（EMQX 5.x Authentication → HTTP Server POST 参数子集）。
 *
 * <p>约定：{@code username = "{productCode}/{deviceName}"}；
 * {@code password = HMAC-SHA256(deviceSecret, username)} 的十六进制小写——
 * 设备侧不传原始 secret， Broker 与服务间内网传输亦不暴露凭据本体。</p>
 *
 * @author BlueWhale
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EmqxAuthRequest {

    /** 客户端ID（设备侧生成，透传记录用） */
    private String clientId;

    /** 用户名：{productCode}/{deviceName} */
    private String username;

    /** 口令：HMAC-SHA256(deviceSecret, username) 十六进制小写 */
    private String password;
}
