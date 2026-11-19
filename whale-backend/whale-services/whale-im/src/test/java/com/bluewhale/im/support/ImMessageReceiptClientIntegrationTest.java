package com.bluewhale.im.support;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.discovery.DiscoveryClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * IM 回执上报客户端集成测试：用本地 HTTP 服务模拟消息服务回执接口，校验线上报协议。
 *
 * <p>覆盖链路终点：whale-im → 消息服务 {@code /message/v1/internal/receipt} 的
 * 租户头、回执字段、幂等键与过滤规则。</p>
 *
 * @author BlueWhale
 */
class ImMessageReceiptClientIntegrationTest {

    private static final long TENANT_ID = 7L;

    private final BlockingQueue<CapturedRequest> captured = new LinkedBlockingQueue<>();

    private HttpServer server;

    private ImMessageReceiptClient client;

    /**
     * 启动本地模拟消息服务并构造客户端（显式 base-url，跳过服务发现）。
     *
     * @throws IOException 端口绑定失败
     */
    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/message/v1/internal/receipt", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            captured.add(new CapturedRequest(exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("X-Tenant-Id"), body));
            byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        client = new ImMessageReceiptClient(mock(DiscoveryClient.class),
                "http://127.0.0.1:" + server.getAddress().getPort(), "whale-service-message", true, 2L);
    }

    @AfterEach
    void tearDown() {
        client.shutdown();
        server.stop(0);
    }

    @Test
    @DisplayName("送达回执上报：租户头、clientMsgId、DELIVERED 与幂等键")
    void shouldReportDeliveredWithClientMsgId() throws Exception {
        client.reportDelivered(TENANT_ID, "msg-88-456", 456L);

        CapturedRequest request = awaitRequest();
        assertEquals("POST", request.method());
        assertEquals("/message/v1/internal/receipt", request.path());
        assertEquals("7", request.tenantHeader());
        assertTrue(request.body().contains("\"tenantId\":7"));
        assertTrue(request.body().contains("\"channel\":\"IM\""));
        assertTrue(request.body().contains("\"providerMessageId\":\"msg-88-456\""));
        assertTrue(request.body().contains("\"status\":\"DELIVERED\""));
        assertTrue(request.body().contains("\"receiptId\":\"msg-88-456:DELIVERED\""));
    }

    @Test
    @DisplayName("归属用户不一致或非消息服务标识的回执不上报")
    void shouldFilterMismatchedReceipts() throws Exception {
        client.reportDelivered(TENANT_ID, "msg-88-456", 999L);
        client.reportDelivered(TENANT_ID, "pm-ASSIGN-101-1", 1L);
        client.reportDelivered(TENANT_ID, "msg-88-456", 456L);

        CapturedRequest request = awaitRequest();
        assertTrue(request.body().contains("\"providerMessageId\":\"msg-88-456\""));
        assertNull(captured.poll(300, TimeUnit.MILLISECONDS), "过滤项不应上报");
    }

    @Test
    @DisplayName("已读回执批量上报，跳过非消息服务标识")
    void shouldReportReadInBatch() throws Exception {
        client.reportRead(TENANT_ID, List.of("msg-88-456", "msg-89-457", "pm-ASSIGN-101-1"));

        CapturedRequest request = awaitRequest();
        assertTrue(request.body().contains("\"status\":\"READ\""));
        assertTrue(request.body().contains("\"receiptId\":\"msg-88-456:READ\""));
        assertTrue(request.body().contains("\"receiptId\":\"msg-89-457:READ\""));
        assertFalse(request.body().contains("pm-ASSIGN-101-1"));
    }

    /**
     * 等待下一条上报请求。
     *
     * @return 捕获到的请求
     * @throws InterruptedException 等待被中断
     */
    private CapturedRequest awaitRequest() throws InterruptedException {
        CapturedRequest request = captured.poll(5, TimeUnit.SECONDS);
        assertNotNull(request, "5 秒内未收到回执上报请求");
        return request;
    }

    /**
     * 捕获到的回执请求。
     *
     * @param method       方法
     * @param path         路径
     * @param tenantHeader 租户头
     * @param body         请求体
     */
    private record CapturedRequest(String method, String path, String tenantHeader, String body) {
    }
}
