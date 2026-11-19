package com.bluewhale.message.support;

import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * {@link MessageArchiveClient} 归档上传客户端单元测试。
 *
 * @author BlueWhale
 */
class MessageArchiveClientTest {

    private static final String UPLOAD_URL = "http://file.local/file/v1/internal/upload";

    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://file.local");

    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    private final MessageArchiveClient client = new MessageArchiveClient(builder.build());

    @Test
    @DisplayName("上传成功返回 data 中的 objectKey")
    void shouldReturnObjectKeyOnSuccess() {
        server.expect(requestTo(UPLOAD_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"code\":0,\"data\":{\"objectKey\":\"k-1\"}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> data = client.upload(
                "line\n".getBytes(StandardCharsets.UTF_8), "a.ndjson", "bucket-a");

        assertEquals("k-1", data.get("objectKey"));
        server.verify();
    }

    @Test
    @DisplayName("业务码非 0 时抛 BizException")
    void shouldThrowWhenBusinessCodeFails() {
        server.expect(requestTo(UPLOAD_URL))
                .andRespond(withSuccess("{\"code\":500,\"message\":\"bucket missing\"}",
                        MediaType.APPLICATION_JSON));

        assertThrows(BizException.class, () -> client.upload(
                "x".getBytes(StandardCharsets.UTF_8), "a.ndjson", "bucket-a"));
    }

    @Test
    @DisplayName("HTTP 异常时抛 BizException")
    void shouldThrowWhenHttpError() {
        server.expect(requestTo(UPLOAD_URL))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThrows(BizException.class, () -> client.upload(
                "x".getBytes(StandardCharsets.UTF_8), "a.ndjson", "bucket-a"));
    }
}
