package com.bluewhale.gateway.accesslist;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 访问名单响应契约测试（E-4）：以 auth 实际下发形状为样本，字段名统一取自
 * {@code GatewayContract}，auth 侧改名会同时破坏两侧契约测试。
 *
 * @author BlueWhale
 */
class AuthGatewayAccessListClientTest {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

    @Test
    @DisplayName("parseRules：解析 Result<List> 形状（data 直接是数组）")
    void shouldParseAuthShapedWhitelist() {
        AuthGatewayAccessListClient client = new AuthGatewayAccessListClient(
                new GatewayAccessListProperties(), WebClient.builder(), OBJECT_MAPPER);
        String body = "{\"code\":0,\"message\":\"ok\",\"data\":["
                + "{\"pathPattern\":\"/auth/v1/login\",\"httpMethod\":\"POST\",\"scope\":\"PLATFORM\",\"tenantId\":null},"
                + "{\"pathPattern\":\"/brand/v1/resolve\",\"httpMethod\":\"*\",\"scope\":\"TENANT\",\"tenantId\":2}"
                + "]}";

        List<GatewayAccessListRule> rules = client.parseRules(body);

        assertEquals(2, rules.size());
        assertEquals("/auth/v1/login", rules.get(0).pathPattern());
        assertEquals("POST", rules.get(0).httpMethod());
        assertEquals("PLATFORM", rules.get(0).scope());
        assertEquals(2L, rules.get(1).tenantId());
    }

    @Test
    @DisplayName("parseRules：业务码非 0 抛错，调用方保留上一份快照")
    void shouldRejectNonSuccessCode() {
        AuthGatewayAccessListClient client = new AuthGatewayAccessListClient(
                new GatewayAccessListProperties(), WebClient.builder(), OBJECT_MAPPER);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> client.parseRules("{\"code\":500,\"message\":\"error\",\"data\":[]}"));
    }
}
