package com.bluewhale.twin.support;

import com.bluewhale.twin.config.TwinProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 告警 webhook 投递器单测：请求形态、失败返回与地址校验。
 *
 * @author BlueWhale
 */
class TwinAlertWebhookSenderTest {

    private static final String URL = "https://hook.example.com/twin/alert";

    private MockRestServiceServer server;

    private TwinAlertWebhookSender sender;

    @BeforeEach
    void setUp() {
        TwinProperties properties = new TwinProperties();
        properties.getAlertNotify().setWebhookToken("secret-token");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        sender = new TwinAlertWebhookSender(properties, builder.build());
    }

    @Test
    void sendsJsonWithBearerTokenAndLevelHeader() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer secret-token"))
                .andExpect(header("X-Twin-Alert-Level", "critical"))
                .andExpect(content().json("{\"level\":\"critical\",\"stateKey\":\"color\"}", false))
                .andRespond(withSuccess());

        assertTrue(sender.send(URL, payload()));

        server.verify();
    }

    @Test
    void returnsFalseOnServerError() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertFalse(sender.send(URL, payload()));
    }

    @Test
    void skipsNonHttpUrlWithoutRequest() {
        assertFalse(sender.send("ftp://hook.example.com/twin/alert", payload()));
        assertFalse(sender.send("https://user:pass@hook.example.com/twin/alert", payload()));
        assertFalse(sender.send(" ", payload()));

        // 未发出任何请求
        server.verify();
    }

    /**
     * 构造告警载荷（与 TwinAlertNotifyService 组包口径一致的子集）。
     *
     * @return 载荷
     */
    private static Map<String, Object> payload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "twin.alert");
        payload.put("stateKey", "color");
        payload.put("level", "critical");
        return payload;
    }
}
