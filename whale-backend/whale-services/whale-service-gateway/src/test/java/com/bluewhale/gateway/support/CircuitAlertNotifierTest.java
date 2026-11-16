package com.bluewhale.gateway.support;

import com.bluewhale.gateway.config.CircuitBreakerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CircuitAlertNotifier} 单元测试：打开/恢复记录的字段与指纹、未配置地址跳过、
 * 远端失败降级为 WARN 不抛异常、内网令牌头附加条件。
 *
 * @author BlueWhale
 */
class CircuitAlertNotifierTest {

    /** 捕获到的最近一次出站请求 */
    private final AtomicReference<ClientRequest> captured = new AtomicReference<>();

    /** 最近一次响应（由用例注入） */
    private volatile Mono<ClientResponse> response;

    private CircuitBreakerProperties properties;

    private CircuitAlertNotifier notifier;

    @BeforeEach
    void setUp() {
        properties = new CircuitBreakerProperties();
        properties.setAlertBaseUrl("http://bw-alert:9103");
        response = Mono.just(ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", "application/json").body("{\"code\":0}").build());
        notifier = new CircuitAlertNotifier(properties, stubClient());
    }

    /**
     * 构造捕获请求的 WebClient 桩：不真正出网，把请求存入 {@code captured} 后返回预设响应
     *
     * @return WebClient
     */
    private WebClient stubClient() {
        return WebClient.builder().exchangeFunction(request -> {
            captured.set(request);
            return response;
        }).build();
    }

    /**
     * 等待异步上报落网（boundedElastic 上执行，轮询至捕获到请求）
     */
    private void awaitCaptured() {
        for (int i = 0; i < 100 && captured.get() == null; i++) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    @Test
    @DisplayName("打开告警请求：POST 到 alert 内网记录接口，未配置内网令牌时不附加令牌头")
    void shouldPostToInternalRecordPath() {
        notifier.notifyOpenAsync("whale-audit");
        awaitCaptured();

        ClientRequest request = captured.get();
        assertEquals("http://bw-alert:9103/alert/internal/record", request.url().toString());
        assertEquals("POST", request.method().name());
        // 内网令牌来自配置项（默认空）：未配置时不带令牌头（dev 侧 alert 按空令牌 fail-open）
        assertNull(request.headers().getFirst("X-Internal-Token"));
    }

    @Test
    @DisplayName("配置了内网令牌时随告警请求发送 X-Internal-Token")
    void shouldAttachInternalTokenWhenConfigured() {
        properties.setInternalToken("unit-test-internal-token");
        notifier = new CircuitAlertNotifier(properties, stubClient());

        notifier.notifyOpenAsync("whale-audit");
        awaitCaptured();

        assertEquals("unit-test-internal-token",
                captured.get().headers().getFirst("X-Internal-Token"));
    }

    @Test
    @DisplayName("打开记录：来源 gateway、级别 ERROR、指纹 gateway-circuit-{route}、内容含路由")
    void openBodyShouldCarryErrorLevelAndFingerprint() {
        Map<String, Object> body = CircuitAlertNotifier.buildBody("ERROR",
                "gateway:circuit:whale-audit", "gateway-circuit-whale-audit",
                "网关熔断打开：route=whale-audit（失败率达阈值，请求已短路降级 503）");

        assertEquals("gateway", body.get("source"));
        assertEquals("ERROR", body.get("alertLevel"));
        assertEquals("gateway:circuit:whale-audit", body.get("ruleCode"));
        assertEquals("gateway-circuit-whale-audit", body.get("fingerprint"));
        assertTrue(String.valueOf(body.get("content")).contains("whale-audit"));
        assertTrue(String.valueOf(body.get("triggerTime")).startsWith("20"));
    }

    @Test
    @DisplayName("恢复记录：级别 INFO 且指纹与打开告警不同（不被打开告警的去重窗口吞掉）")
    void recoveredBodyShouldUseDistinctFingerprintAndInfoLevel() {
        Map<String, Object> body = CircuitAlertNotifier.buildBody("INFO",
                "gateway:circuit:whale-audit", "gateway-circuit-recovered-whale-audit",
                "网关熔断恢复：route=whale-audit（半开探测成功，已恢复闭合）");

        assertEquals("INFO", body.get("alertLevel"));
        assertFalse(body.get("fingerprint").equals("gateway-circuit-whale-audit"));
        assertTrue(String.valueOf(body.get("content")).contains("网关熔断恢复"));
    }

    @Test
    @DisplayName("alert-base-url 未配置时整体跳过，不出站")
    void shouldSkipWhenBaseUrlBlank() {
        properties.setAlertBaseUrl("");
        notifier.notifyOpenAsync("whale-audit");

        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertNull(captured.get(), "未配置地址不应出站");
    }

    @Test
    @DisplayName("远端 500 时降级为 WARN，不向调用方抛异常")
    void shouldSwallowRemoteFailure() {
        response = Mono.just(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).body("boom").build());
        boolean sent = notifier.send("whale-audit", "ERROR", "gateway:circuit:whale-audit",
                "gateway-circuit-whale-audit", "content");
        assertFalse(sent);
    }

    @Test
    @DisplayName("远端正常返回时受理成功")
    void shouldReturnTrueWhenAccepted() {
        boolean sent = notifier.send("whale-audit", "ERROR", "gateway:circuit:whale-audit",
                "gateway-circuit-whale-audit", "content");
        assertTrue(sent);
    }
}
