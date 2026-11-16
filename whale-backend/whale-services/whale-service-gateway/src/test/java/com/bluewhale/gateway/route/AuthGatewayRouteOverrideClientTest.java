package com.bluewhale.gateway.route;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 路由覆盖响应契约测试（E-4 / E-6）：auth 返回 {@code Result<List>}，即 {@code data} 是数组。
 *
 * <p>历史实现按 {@code data.items[]} 解析，与 auth 实际形状不一致，导致网关永远保留空快照。
 * 本测试以 auth 实际 JSON 为样本锁定形状，防止再次漂移。</p>
 *
 * @author BlueWhale
 */
class AuthGatewayRouteOverrideClientTest {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

    @Test
    @DisplayName("parseRules：解析 auth 实际形状 data[]，含生效窗口与版本")
    void shouldParseAuthShapedList() {
        AuthGatewayRouteOverrideClient client = new AuthGatewayRouteOverrideClient(
                new RouteOverrideProperties(), WebClient.builder(), OBJECT_MAPPER);
        String body = "{\"code\":0,\"message\":\"ok\",\"data\":["
                + "{\"routeId\":\"whale-demo\",\"enabled\":false,"
                + "\"effectiveFrom\":\"2026-11-13T08:00:00\",\"effectiveTo\":\"2026-11-13T22:00:00\",\"version\":3},"
                + "{\"routeId\":\"whale-audit\",\"enabled\":true,"
                + "\"effectiveFrom\":null,\"effectiveTo\":null,\"version\":1}"
                + "]}";

        List<RouteOverrideRule> rules = client.parseRules(body);

        assertEquals(2, rules.size());
        assertEquals("whale-demo", rules.get(0).routeId());
        assertEquals(false, rules.get(0).enabled());
        assertEquals(LocalDateTime.of(2026, 11, 13, 8, 0), rules.get(0).effectiveFrom());
        assertEquals(3L, rules.get(0).version());
        assertTrue(rules.get(1).enabled());
        assertNull(rules.get(1).effectiveFrom());
    }

    @Test
    @DisplayName("parseRules：兼容 data.items[] 包装（向后兼容）")
    void shouldParseWrappedItemsShape() {
        AuthGatewayRouteOverrideClient client = new AuthGatewayRouteOverrideClient(
                new RouteOverrideProperties(), WebClient.builder(), OBJECT_MAPPER);
        String body = "{\"code\":0,\"data\":{\"items\":["
                + "{\"routeId\":\"whale-demo\",\"enabled\":true,\"version\":2}]}}";

        List<RouteOverrideRule> rules = client.parseRules(body);

        assertEquals(1, rules.size());
        assertEquals("whale-demo", rules.get(0).routeId());
        assertEquals(2L, rules.get(0).version());
    }
}
