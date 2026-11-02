package com.bluewhale.doc.infrastructure.client;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.doc.config.DocProperties;
import com.bluewhale.doc.support.DocOperator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * {@link DocWorkflowClient} 行为断言：本地 HttpServer 模拟 whale-workflow。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocWorkflowClientTest {

    /** 捕获的最近一次请求 */
    record Captured(String path, byte[] body, Map<String, List<String>> headers) {
    }

    @Mock
    private DiscoveryClient discoveryClient;

    @Mock
    private ServiceInstance instance;

    private HttpServer server;

    private DocWorkflowClient client;

    private final DocProperties docProperties = new DocProperties();

    private volatile Captured last;

    private volatile Consumer<HttpExchange> responder = exchange -> json(exchange, "{\"code\":0,\"data\":{}}");

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", this::handle);
        server.start();
        when(instance.getUri()).thenReturn(URI.create("http://localhost:" + server.getAddress().getPort()));
        when(discoveryClient.getInstances("whale-workflow")).thenReturn(List.of(instance));
        client = new DocWorkflowClient(discoveryClient, docProperties);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        TenantContext.clear();
    }

    private void handle(HttpExchange exchange) throws IOException {
        last = new Captured(exchange.getRequestURI().getPath(),
                exchange.getRequestBody().readAllBytes(), exchange.getRequestHeaders());
        responder.accept(exchange);
    }

    private static void json(HttpExchange exchange, String body) {
        try {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        } catch (IOException ex) {
            throw new java.io.UncheckedIOException(ex);
        }
    }

    private static DocOperator operator() {
        return new DocOperator(7L, "alice", "doc:publish", "ADMIN");
    }

    @Test
    void startShouldPostBodyAndReturnInstanceId() {
        responder = exchange -> json(exchange, "{\"code\":0,\"data\":{\"id\":123}}");
        TenantContext.set(9L);

        Long instanceId = client.start(1L, "发布", "page-1", Map.of("k", "v"), operator());

        assertThat(instanceId).isEqualTo(123L);
        assertThat(last.path()).isEqualTo("/workflow/instance");
        String body = new String(last.body(), StandardCharsets.UTF_8);
        assertThat(body).contains("\"definitionId\":1").contains("\"title\":\"发布\"")
                .contains("\"businessKey\":\"page-1\"").contains("\"k\":\"v\"");
        assertThat(last.headers().get("X-User-Id")).containsExactly("7");
        assertThat(last.headers().get("X-Username")).containsExactly("alice");
        assertThat(last.headers().get("X-Permission-Codes")).containsExactly("doc:publish");
        assertThat(last.headers().get("X-User-Roles")).containsExactly("ADMIN");
        assertThat(last.headers().get("X-Tenant-Id")).containsExactly("9");
    }

    @Test
    void startShouldSkipHeadersForNullOperator() {
        responder = exchange -> json(exchange, "{\"code\":0,\"data\":{\"id\":1}}");

        assertThat(client.start(1L, "t", "bk", Map.of(), null)).isEqualTo(1L);

        assertThat(last.headers().get("X-User-Id")).isNull();
        assertThat(last.headers().get("X-Username")).isNull();
        assertThat(last.headers().get("X-Tenant-Id")).isNull();
    }

    @Test
    void startShouldSkipBlankOperatorFields() {
        responder = exchange -> json(exchange, "{\"code\":0,\"data\":{\"id\":1}}");

        DocOperator blank = new DocOperator(null, " ", null, "");
        assertThat(client.start(1L, "t", "bk", Map.of(), blank)).isEqualTo(1L);

        assertThat(last.headers().get("X-User-Id")).isNull();
        assertThat(last.headers().get("X-Username")).isNull();
        assertThat(last.headers().get("X-Permission-Codes")).isNull();
        assertThat(last.headers().get("X-User-Roles")).isNull();
    }

    @Test
    void startShouldRejectEmptyResponse() {
        responder = exchange -> json(exchange, "null");

        assertThatThrownBy(() -> client.start(1L, "t", "bk", Map.of(), operator()))
                .isInstanceOf(BizException.class)
                .hasMessage("发布审批调用失败: 空响应");
    }

    @Test
    void startShouldRejectNonZeroCode() {
        responder = exchange -> json(exchange, "{\"code\":500,\"message\":\"boom\"}");

        assertThatThrownBy(() -> client.start(1L, "t", "bk", Map.of(), operator()))
                .isInstanceOf(BizException.class)
                .hasMessage("发布审批调用失败: boom");
    }

    @Test
    void startShouldRejectMissingInstanceId() {
        responder = exchange -> json(exchange, "{\"code\":0,\"data\":{}}");

        assertThatThrownBy(() -> client.start(1L, "t", "bk", Map.of(), operator()))
                .isInstanceOf(BizException.class)
                .hasMessage("发布审批调用失败: 响应缺少实例ID");
    }

    @Test
    void startShouldRejectIllegalInstanceId() {
        responder = exchange -> json(exchange, "{\"code\":0,\"data\":{\"id\":\"abc\"}}");

        assertThatThrownBy(() -> client.start(1L, "t", "bk", Map.of(), operator()))
                .isInstanceOf(BizException.class)
                .hasMessage("发布审批调用失败: 实例ID非法");
    }

    @Test
    void startShouldFailWhenServiceUnavailable() {
        when(discoveryClient.getInstances("whale-workflow")).thenReturn(List.of());

        assertThatThrownBy(() -> client.start(1L, "t", "bk", Map.of(), operator()))
                .isInstanceOf(BizException.class)
                .hasMessage("发布审批调用失败: 工作流服务不可用");
    }

    @Test
    void fetchStatusShouldReturnStatusNumber() {
        responder = exchange -> json(exchange, "{\"code\":0,\"data\":{\"status\":1}}");

        assertThat(client.fetchStatus(5L)).isEqualTo(1);
        assertThat(last.path()).isEqualTo("/workflow/internal/instance/5/detail");
    }

    @Test
    void fetchStatusShouldReturnNullOnNullResponse() {
        responder = exchange -> json(exchange, "null");

        assertThat(client.fetchStatus(5L)).isNull();
    }

    @Test
    void fetchStatusShouldReturnNullOnNonZeroCode() {
        responder = exchange -> json(exchange, "{\"code\":500}");

        assertThat(client.fetchStatus(5L)).isNull();
    }

    @Test
    void fetchStatusShouldReturnNullWhenStatusNotNumber() {
        responder = exchange -> json(exchange, "{\"code\":0,\"data\":{\"status\":\"1\"}}");

        assertThat(client.fetchStatus(5L)).isNull();
    }

    @Test
    void fetchStatusShouldReturnNullWhenDataMissing() {
        responder = exchange -> json(exchange, "{\"code\":0}");

        assertThat(client.fetchStatus(5L)).isNull();
    }
}
