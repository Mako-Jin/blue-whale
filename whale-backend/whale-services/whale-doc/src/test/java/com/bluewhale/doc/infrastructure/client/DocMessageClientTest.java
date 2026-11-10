package com.bluewhale.doc.infrastructure.client;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.doc.config.DocProperties;
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
 * {@link DocMessageClient} 行为断言：本地 HttpServer 模拟 whale-message。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocMessageClientTest {

    /** 捕获的最近一次请求 */
    record Captured(String path, byte[] body, Map<String, List<String>> headers) {
    }

    @Mock
    private DiscoveryClient discoveryClient;

    @Mock
    private ServiceInstance instance;

    private HttpServer server;

    private DocMessageClient client;

    private final DocProperties docProperties = new DocProperties();

    private volatile Captured last;

    private volatile Consumer<HttpExchange> responder = exchange -> json(exchange, "{\"code\":0,\"data\":{}}");

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", this::handle);
        server.start();
        when(instance.getUri()).thenReturn(URI.create("http://localhost:" + server.getAddress().getPort()));
        when(discoveryClient.getInstances("whale-message")).thenReturn(List.of(instance));
        client = new DocMessageClient(discoveryClient, docProperties);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
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

    @Test
    void sendShouldPostBodyAndReturnOutcome() {
        responder = exchange -> json(exchange, "{\"code\":0,\"data\":{\"duplicate\":true,\"messageId\":88}}");

        DocMessageClient.SendOutcome outcome = client.send(3L, "DOC_COMMENT", "1", "key-1",
                "标题", "正文", List.of("INAPP"), List.of(7L, 8L));

        assertThat(outcome.duplicate()).isTrue();
        assertThat(outcome.messageId()).isEqualTo("88");
        assertThat(last.path()).isEqualTo("/message/internal/send");
        assertThat(last.headers().get("X-Tenant-Id")).containsExactly("3");
        String body = new String(last.body(), StandardCharsets.UTF_8);
        assertThat(body).contains("\"tenantId\":3").contains("\"bizType\":\"DOC_COMMENT\"")
                .contains("\"idempotencyKey\":\"key-1\"")
                .contains("\"recipients\":[{\"userId\":7},{\"userId\":8}]");
    }

    @Test
    void sendShouldTreatMissingDataAsEmpty() {
        responder = exchange -> json(exchange, "{\"code\":0}");

        DocMessageClient.SendOutcome outcome = client.send(3L, "DOC_COMMENT", "1", "key-1",
                "标题", "正文", List.of("INAPP"), List.of(7L));

        assertThat(outcome.duplicate()).isFalse();
        assertThat(outcome.messageId()).isEqualTo("null");
    }

    @Test
    void sendShouldRejectEmptyResponse() {
        responder = exchange -> json(exchange, "null");

        assertThatThrownBy(() -> client.send(3L, "DOC_COMMENT", "1", "key-1",
                "标题", "正文", List.of("INAPP"), List.of(7L)))
                .isInstanceOf(BizException.class)
                .hasMessage("消息服务响应为空");
    }

    @Test
    void sendShouldRejectNonZeroCode() {
        responder = exchange -> json(exchange, "{\"code\":500,\"message\":\"boom\"}");

        assertThatThrownBy(() -> client.send(3L, "DOC_COMMENT", "1", "key-1",
                "标题", "正文", List.of("INAPP"), List.of(7L)))
                .isInstanceOf(BizException.class)
                .hasMessage("消息服务返回失败: boom");
    }

    @Test
    void sendShouldRejectNonNumericCode() {
        responder = exchange -> json(exchange, "{\"code\":\"0\",\"data\":{}}");

        assertThatThrownBy(() -> client.send(3L, "DOC_COMMENT", "1", "key-1",
                "标题", "正文", List.of("INAPP"), List.of(7L)))
                .isInstanceOf(BizException.class)
                .hasMessage("消息服务返回失败: null");
    }

    @Test
    void sendShouldFailWhenServiceUnavailable() {
        when(discoveryClient.getInstances("whale-message")).thenReturn(List.of());

        assertThatThrownBy(() -> client.send(3L, "DOC_COMMENT", "1", "key-1",
                "标题", "正文", List.of("INAPP"), List.of(7L)))
                .isInstanceOf(BizException.class)
                .hasMessage("消息服务不可用");
    }
}
