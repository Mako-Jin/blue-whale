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
 * {@link DocArchiveClient} 行为断言：本地 HttpServer 模拟 whale-archive。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocArchiveClientTest {

    /** 捕获的最近一次请求 */
    record Captured(String path, byte[] body, Map<String, List<String>> headers) {
    }

    @Mock
    private DiscoveryClient discoveryClient;

    @Mock
    private ServiceInstance instance;

    private HttpServer server;

    private DocArchiveClient client;

    private final DocProperties docProperties = new DocProperties();

    private volatile Captured last;

    private volatile Consumer<HttpExchange> responder = exchange -> json(exchange, "{\"code\":0,\"data\":{}}");

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", this::handle);
        server.start();
        when(instance.getUri()).thenReturn(URI.create("http://localhost:" + server.getAddress().getPort()));
        when(discoveryClient.getInstances("whale-archive")).thenReturn(List.of(instance));
        client = new DocArchiveClient(discoveryClient, docProperties);
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

    private static Map<String, Object> payload() {
        return Map.of("sourceType", "DOC_PAGE", "sourceId", "1");
    }

    @Test
    void ingestShouldPostPayloadAndReturnOutcome() {
        responder = exchange -> json(exchange,
                "{\"code\":0,\"data\":{\"itemId\":11,\"snapshotFileId\":22,\"created\":true}}");

        DocArchiveClient.IngestOutcome outcome = client.ingest(3L, payload());

        assertThat(outcome.itemId()).isEqualTo("11");
        assertThat(outcome.snapshotFileId()).isEqualTo("22");
        assertThat(outcome.created()).isTrue();
        assertThat(last.path()).isEqualTo("/archive/internal/items");
        assertThat(last.headers().get("X-Tenant-Id")).containsExactly("3");
        assertThat(new String(last.body(), StandardCharsets.UTF_8)).contains("\"sourceType\":\"DOC_PAGE\"");
    }

    @Test
    void ingestShouldMapNullFieldsToNull() {
        responder = exchange -> json(exchange, "{\"code\":0,\"data\":{}}");

        DocArchiveClient.IngestOutcome outcome = client.ingest(3L, payload());

        assertThat(outcome.itemId()).isNull();
        assertThat(outcome.snapshotFileId()).isNull();
        assertThat(outcome.created()).isFalse();
    }

    @Test
    void ingestShouldRejectEmptyResponse() {
        responder = exchange -> json(exchange, "null");

        assertThatThrownBy(() -> client.ingest(3L, payload()))
                .isInstanceOf(BizException.class)
                .hasMessage("档案服务响应为空");
    }

    @Test
    void ingestShouldRejectNonZeroCode() {
        responder = exchange -> json(exchange, "{\"code\":500,\"message\":\"boom\"}");

        assertThatThrownBy(() -> client.ingest(3L, payload()))
                .isInstanceOf(BizException.class)
                .hasMessage("档案服务返回失败: boom");
    }

    @Test
    void ingestShouldRejectMalformedData() {
        responder = exchange -> json(exchange, "{\"code\":0,\"data\":\"oops\"}");

        assertThatThrownBy(() -> client.ingest(3L, payload()))
                .isInstanceOf(BizException.class)
                .hasMessage("档案服务返回数据格式异常");
    }

    @Test
    void ingestShouldFailWhenServiceUnavailable() {
        when(discoveryClient.getInstances("whale-archive")).thenReturn(List.of());

        assertThatThrownBy(() -> client.ingest(3L, payload()))
                .isInstanceOf(BizException.class)
                .hasMessage("档案服务不可用");
    }
}
