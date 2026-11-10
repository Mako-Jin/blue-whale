package com.bluewhale.doc.infrastructure.client;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link DocFileClient} 行为断言：本地 HttpServer 模拟 whale-file，Mockito 模拟服务发现。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocFileClientTest {

    /** 捕获的最近一次请求 */
    record Captured(String path, byte[] body, Map<String, List<String>> headers) {
    }

    @Mock
    private DiscoveryClient discoveryClient;

    @Mock
    private ServiceInstance instance;

    private HttpServer server;

    private DocFileClient client;

    private volatile Captured last;

    private volatile Consumer<HttpExchange> responder = exchange -> json(exchange, "{\"code\":0,\"data\":{}}");

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", this::handle);
        server.start();
        when(instance.getUri()).thenReturn(URI.create("http://localhost:" + server.getAddress().getPort()));
        when(discoveryClient.getInstances("whale-file")).thenReturn(List.of(instance));
        client = new DocFileClient(discoveryClient);
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

    private static void bytes(HttpExchange exchange, byte[] payload) {
        try {
            exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
            exchange.sendResponseHeaders(200, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        } catch (IOException ex) {
            throw new java.io.UncheckedIOException(ex);
        }
    }

    private static void empty(HttpExchange exchange) {
        try {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        } catch (IOException ex) {
            throw new java.io.UncheckedIOException(ex);
        }
    }

    private MultipartFile file(String name, String contentType) {
        return new org.springframework.mock.web.MockMultipartFile(
                "file", name, contentType, "hello".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void uploadShouldPostMultipartAndParseResult() throws IOException {
        responder = exchange -> json(exchange,
                "{\"code\":0,\"data\":{\"id\":99,\"originalName\":\"a.txt\",\"contentType\":\"text/plain\",\"fileSize\":5}}");

        DocFileClient.UploadedFile result = client.upload(file("a.txt", "text/plain"), "doc", 7L, 3L);

        assertThat(result.fileId()).isEqualTo(99L);
        assertThat(result.originalName()).isEqualTo("a.txt");
        assertThat(result.contentType()).isEqualTo("text/plain");
        assertThat(result.fileSize()).isEqualTo(5L);
        assertThat(last.path()).isEqualTo("/file/internal/upload");
        assertThat(new String(last.body(), StandardCharsets.UTF_8)).contains("filename=\"a.txt\"");
        assertThat(last.headers().get("X-User-Id")).containsExactly("7");
        assertThat(last.headers().get("X-Tenant-Id")).containsExactly("3");
    }

    @Test
    void uploadShouldFallbackForMissingFields() throws IOException {
        responder = exchange -> json(exchange, "{\"code\":0,\"data\":{\"id\":\"12\",\"fileSize\":\"5\"}}");

        DocFileClient.UploadedFile result = client.upload(file("a.txt", "text/plain"), "doc", 7L, 3L);

        assertThat(result.fileId()).isEqualTo(12L);
        assertThat(result.originalName()).isEqualTo("a.txt");
        assertThat(result.contentType()).isEqualTo("text/plain");
        assertThat(result.fileSize()).isEqualTo(5L);
    }

    @Test
    void uploadShouldMapIllegalFileSizeToNull() throws IOException {
        responder = exchange -> json(exchange, "{\"code\":0,\"data\":{\"id\":1,\"fileSize\":\"abc\"}}");

        DocFileClient.UploadedFile result = client.upload(file("a.txt", null), "doc", 7L, 3L);

        assertThat(result.fileId()).isEqualTo(1L);
        assertThat(result.fileSize()).isNull();
    }

    @Test
    void uploadShouldMapMissingIdsToNull() {
        responder = exchange -> json(exchange, "{\"code\":0,\"data\":{}}");

        DocFileClient.UploadedFile result = client.upload(file("a.txt", "text/plain"), "doc", 7L, 3L);

        assertThat(result.fileId()).isNull();
        assertThat(result.fileSize()).isNull();
    }

    @Test
    void uploadShouldRejectNonZeroBusinessCode() throws IOException {
        responder = exchange -> json(exchange, "{\"code\":500,\"message\":\"boom\"}");

        assertThatThrownBy(() -> client.upload(file("a.txt", "text/plain"), "doc", 7L, 3L))
                .isInstanceOf(BizException.class)
                .hasMessage("boom")
                .extracting(ex -> ((BizException) ex).getCode()).isEqualTo(500);
    }

    @Test
    void uploadShouldRejectMalformedData() throws IOException {
        responder = exchange -> json(exchange, "{\"code\":0,\"data\":\"oops\"}");

        assertThatThrownBy(() -> client.upload(file("a.txt", "text/plain"), "doc", 7L, 3L))
                .isInstanceOf(BizException.class)
                .hasMessage("文件服务返回数据格式异常");
    }

    @Test
    void uploadShouldRejectEmptyResponse() {
        responder = exchange -> json(exchange, "null");

        assertThatThrownBy(() -> client.upload(file("a.txt", "text/plain"), "doc", 7L, 3L))
                .isInstanceOf(BizException.class)
                .hasMessage("文件服务响应为空");
    }

    @Test
    void uploadShouldFailWhenReadingFileFails() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getBytes()).thenThrow(new IOException("disk read fail"));

        assertThatThrownBy(() -> client.upload(file, "doc", 7L, 3L))
                .isInstanceOf(BizException.class)
                .hasMessage("读取上传文件失败")
                .extracting(ex -> ((BizException) ex).getCode()).isEqualTo(ErrorCode.SYSTEM_ERROR.getCode());
    }

    @Test
    void uploadShouldFailWhenServiceUnavailable() throws IOException {
        when(discoveryClient.getInstances("whale-file")).thenReturn(List.of());

        assertThatThrownBy(() -> client.upload(file("a.txt", "text/plain"), "doc", 7L, 3L))
                .isInstanceOf(BizException.class)
                .hasMessage("文件服务不可用");
    }

    @Test
    void uploadBytesShouldUploadGivenContent() {
        responder = exchange -> json(exchange,
                "{\"code\":0,\"data\":{\"id\":5,\"originalName\":\"snap.json\",\"contentType\":\"application/json\",\"fileSize\":12}}");

        DocFileClient.UploadedFile result = client.uploadBytes("{}".getBytes(StandardCharsets.UTF_8),
                "snap.json", "application/json", "doc", 7L, 3L);

        assertThat(result.fileId()).isEqualTo(5L);
        assertThat(result.originalName()).isEqualTo("snap.json");
        assertThat(result.contentType()).isEqualTo("application/json");
        assertThat(result.fileSize()).isEqualTo(12L);
        assertThat(last.headers().get("X-User-Id")).containsExactly("7");
        assertThat(last.headers().get("X-Tenant-Id")).containsExactly("3");
    }

    @Test
    void contentShouldReturnRawBytes() {
        responder = exchange -> bytes(exchange, "raw".getBytes(StandardCharsets.UTF_8));

        assertThat(client.content(42L)).isEqualTo("raw".getBytes(StandardCharsets.UTF_8));
        assertThat(last.path()).isEqualTo("/file/internal/42/content");
    }

    @Test
    void contentShouldRejectNullBody() {
        responder = DocFileClientTest::empty;

        assertThatThrownBy(() -> client.content(42L))
                .isInstanceOf(BizException.class)
                .hasMessage("附件内容不存在")
                .extracting(ex -> ((BizException) ex).getCode()).isEqualTo(ErrorCode.NOT_FOUND.getCode());
    }
}
