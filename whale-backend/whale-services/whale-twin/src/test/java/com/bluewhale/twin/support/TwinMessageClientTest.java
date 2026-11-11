package com.bluewhale.twin.support;

import com.bluewhale.twin.config.TwinProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * whale-message 内网客户端单测：请求形态、幂等键、结果解析与失败折算。
 *
 * @author BlueWhale
 */
class TwinMessageClientTest {

    private static final String BASE_URL = "http://127.0.0.1:8094";

    private MockRestServiceServer server;

    private TwinMessageClient client;

    @BeforeEach
    void setUp() {
        TwinProperties properties = new TwinProperties();
        properties.getAlertNotify().setMessageBaseUrl(BASE_URL);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new TwinMessageClient(properties, builder.build());
    }

    @Test
    void sendsInappRequestWithTenantAndIdempotencyKey() {
        server.expect(requestTo(BASE_URL + "/message/internal/send"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Tenant-Id", "1"))
                .andExpect(content().json("{\"bizType\":\"twin-alert\","
                        + "\"idempotencyKey\":\"twin-alert-9001\",\"channels\":[\"INAPP\"]}", false))
                .andRespond(withSuccess("{\"code\":0,\"data\":{\"messageId\":321,\"status\":\"SENT\"}}",
                        MediaType.APPLICATION_JSON));

        TwinMessageClient.SendOutcome outcome = client.sendInapp(1L, "twin-alert-9001",
                "标题", "正文", payload(), List.of(7L));

        assertTrue(outcome.ok());
        assertEquals(321L, outcome.messageId());
        assertEquals("SENT", outcome.status());
        server.verify();
    }

    @Test
    void returnsFailureOnBusinessErrorCode() {
        server.expect(requestTo(BASE_URL + "/message/internal/send"))
                .andRespond(withSuccess("{\"code\":500,\"message\":\"渠道未配置\"}",
                        MediaType.APPLICATION_JSON));

        TwinMessageClient.SendOutcome outcome = client.sendInapp(1L, "twin-alert-9001",
                "标题", "正文", payload(), List.of(7L));

        assertFalse(outcome.ok());
        assertNull(outcome.messageId());
        assertNotNull(outcome.error());
    }

    @Test
    void returnsFailureOnHttpError() {
        server.expect(requestTo(BASE_URL + "/message/internal/send"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertFalse(client.sendInapp(1L, "twin-alert-9001", "标题", "正文",
                payload(), List.of(7L)).ok());
    }

    @Test
    void skipsRequestWhenNoRecipient() {
        TwinMessageClient.SendOutcome outcome = client.sendInapp(1L, "twin-alert-9001",
                "标题", "正文", payload(), List.of());

        assertFalse(outcome.ok());
        assertEquals("无接收人", outcome.error());
        // 未发出任何请求
        server.verify();
    }

    @Test
    void parsesStringUserIdsInPreferenceResolve() {
        // 平台把 Long 序列化成字符串：userId 是 "601"/"602"，只判 Number 会全部丢空
        server.expect(requestTo(BASE_URL + "/message/internal/preferences/resolve"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"code\":0,\"data\":["
                        + "{\"userId\":\"601\",\"channels\":[\"INAPP\"],\"quiet\":false},"
                        + "{\"userId\":\"602\",\"channels\":[\"EMAIL\"],\"quiet\":false},"
                        + "{\"userId\":603,\"channels\":[\"INAPP\"],\"quiet\":true}]}",
                        MediaType.APPLICATION_JSON));

        List<Long> allowed = client.resolveInappRecipients(1L, List.of(601L, 602L), "warn", true);

        // 601 保留（字符串 ID + INAPP）；602 被偏好收紧到 EMAIL；603 未申请不返回
        assertEquals(List.of(601L), allowed);
        server.verify();
    }

    @Test
    void parsesStringMessageIdInSendResult() {
        server.expect(requestTo(BASE_URL + "/message/internal/send"))
                .andRespond(withSuccess("{\"code\":0,\"data\":{\"messageId\":\"2120470057446309889\","
                        + "\"status\":\"SENT\"}}", MediaType.APPLICATION_JSON));

        TwinMessageClient.SendOutcome outcome = client.sendInapp(1L, "twin-alert-9002",
                "标题", "正文", payload(), List.of(7L));

        assertTrue(outcome.ok());
        assertEquals(2120470057446309889L, outcome.messageId());
    }

    /**
     * 构造载荷。
     *
     * @return 载荷
     */
    private static Map<String, Object> payload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "twin.alert");
        payload.put("stateKey", "color");
        return payload;
    }
}
