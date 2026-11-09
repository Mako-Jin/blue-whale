package com.bluewhale.iot.controller;

import com.bluewhale.iot.dto.EmqxAuthRequest;
import com.bluewhale.iot.dto.EmqxWebhookRequest;
import com.bluewhale.iot.dto.TelemetryIngestResult;
import com.bluewhale.iot.service.EmqxAuthService;
import com.bluewhale.iot.service.IotTelemetryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * EMQX 内网回调接口（认证 Webhook / 连接事件 / 消息桥接）。
 *
 * <p>路径在 {@code /iot/internal/**} 下，由 starter 的内网令牌过滤器保护
 * （EMQX 侧 HTTP 请求需携带 {@code X-Internal-Token}）。认证与事件响应体为
 * <b>EMQX 约定形状</b>（{@code {"result":"allow"/"deny"/"ok"}}），不走 Result 包装。</p>
 *
 * <p>EMQX 配置（M2 联调用）：
 * Authentication → HTTP Server → {@code POST http://whale-iot:8088/iot/internal/emqx/auth}；
 * Webhook → {@code POST .../webhook}（事件 {@code client.connected} / {@code client.disconnected}）；
 * 消息桥接 → 同一 Webhook 配 {@code message.publish}（主题过滤 {@code up/property}、
 * {@code up/event}、{@code up/reply}），本服务按主题后缀分流。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@RestController
@RequestMapping("/iot/internal/emqx")
@RequiredArgsConstructor
public class IotInternalEmqxController {

    private final EmqxAuthService emqxAuthService;

    private final IotTelemetryService telemetryService;

    /**
     * 设备接入认证（EMQX HTTP Authentication 回调）。
     *
     * @param request 认证请求（username={productCode}/{deviceName}，password=HMAC）
     * @return EMQX 约定形状 {@code {"result":"allow"}} / {@code {"result":"deny"}}
     */
    @PostMapping("/auth")
    public Map<String, String> auth(@RequestBody EmqxAuthRequest request) {
        boolean allowed = emqxAuthService.authenticate(request.getUsername(), request.getPassword());
        return Map.of("result", allowed ? "allow" : "deny");
    }

    /**
     * 事件回调入口（连接/断开维护在线态；消息发布转遥测服务，其余事件回 200 忽略）。
     *
     * @param request Webhook 事件体
     * @return EMQX 约定形状 {@code {"result":"ok"}}
     */
    @PostMapping("/webhook")
    public Map<String, Object> webhook(@RequestBody EmqxWebhookRequest request) {
        String event = request.getEvent();
        if (event == null) {
            return Map.of("result", "ok");
        }
        switch (event) {
            case "client.connected" -> emqxAuthService.onConnected(request.getUsername());
            case "client.disconnected" -> emqxAuthService.onDisconnected(request.getUsername());
            case "message.publish" -> {
                TelemetryIngestResult result = telemetryService.ingest(
                        request.getUsername(), request.getTopic(), request.getPayload());
                return Map.of("result", "ok", "accepted", result.getAccepted(), "rejected", result.getRejected());
            }
            default -> log.debug("EMQX 事件忽略：{}", event);
        }
        return Map.of("result", "ok");
    }
}
