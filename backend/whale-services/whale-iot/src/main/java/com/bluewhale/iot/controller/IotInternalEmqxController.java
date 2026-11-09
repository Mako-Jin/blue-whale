package com.bluewhale.iot.controller;

import com.bluewhale.iot.dto.EmqxAuthRequest;
import com.bluewhale.iot.dto.EmqxWebhookRequest;
import com.bluewhale.iot.service.EmqxAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * EMQX 内网回调接口（认证 Webhook / 连接事件）。
 *
 * <p>路径在 {@code /iot/internal/**} 下，由 starter 的内网令牌过滤器保护
 * （EMQX 侧 HTTP 请求需携带 {@code X-Internal-Token}）。响应体为
 * <b>EMQX 约定形状</b>（{@code {"result":"allow"/"deny"}}），不走 Result 包装。</p>
 *
 * <p>EMQX 配置（M1 联调用）：
 * Authentication → HTTP Server →
 * {@code POST http://whale-iot:8088/iot/internal/emqx/auth}；
 * Webhook → {@code POST http://whale-iot:8088/iot/internal/emqx/webhook}。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@RestController
@RequestMapping("/iot/internal/emqx")
@RequiredArgsConstructor
public class IotInternalEmqxController {

    private final EmqxAuthService emqxAuthService;

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
     * 连接/断开事件（维护设备在线态；其余事件忽略并回 200，避免 Broker 侧重试风暴）。
     *
     * @param request Webhook 事件体
     * @return EMQX 约定形状 {@code {"result":"ok"}}
     */
    @PostMapping("/webhook")
    public Map<String, String> webhook(@RequestBody EmqxWebhookRequest request) {
        String event = request.getEvent();
        if (event == null) {
            return Map.of("result", "ok");
        }
        switch (event) {
            case "client.connected" -> emqxAuthService.onConnected(request.getUsername());
            case "client.disconnected" -> emqxAuthService.onDisconnected(request.getUsername());
            default -> log.debug("EMQX 事件忽略：{}", event);
        }
        return Map.of("result", "ok");
    }
}
