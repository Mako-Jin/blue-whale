package com.bluewhale.gateway.filter;

import com.bluewhale.gateway.config.WhaleGatewayProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ActuatorInternalOnlyWebFilter} 单元测试（P2-14）：内网放行、公网/代理公网拒绝、
 * 非 actuator 路径放行与开关关闭。
 *
 * @author BlueWhale
 */
class ActuatorInternalOnlyWebFilterTest {

    private static final String EXPECTED_403_BODY = "{\"code\":403,\"message\":\"管理端点仅限内网访问\",\"data\":null}";

    private WhaleGatewayProperties properties;
    private ActuatorInternalOnlyWebFilter filter;
    private final AtomicBoolean chainCalled = new AtomicBoolean(false);
    private final WebFilterChain chain = exchange -> {
        chainCalled.set(true);
        return Mono.empty();
    };

    @BeforeEach
    void setUp() {
        properties = new WhaleGatewayProperties();
        filter = new ActuatorInternalOnlyWebFilter(properties);
        chainCalled.set(false);
    }

    /**
     * 构造带远端地址的模拟请求
     *
     * @param path      请求路径
     * @param remoteIp  直连远端 IP
     * @param forwardedFor X-Forwarded-For 头，可空
     * @return 模拟请求上下文
     */
    private static MockServerWebExchange buildExchange(String path, String remoteIp, String forwardedFor) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.get("http://localhost" + path)
                .remoteAddress(new InetSocketAddress(remoteIp, 5000));
        if (forwardedFor != null) {
            builder.header("X-Forwarded-For", forwardedFor);
        }
        return MockServerWebExchange.from(builder.build());
    }

    @Test
    @DisplayName("filter：回环来源访问 /actuator/health 放行")
    void shouldAllowLoopback() {
        MockServerWebExchange exchange = buildExchange("/actuator/health", "127.0.0.1", null);

        filter.filter(exchange, chain).block();

        assertTrue(chainCalled.get());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("filter：内网直连 + 内网 X-Forwarded-For 放行")
    void shouldAllowInternalWithInternalForwardedFor() {
        MockServerWebExchange exchange = buildExchange("/actuator/prometheus", "10.1.2.3", "192.168.1.7, 10.1.2.3");

        filter.filter(exchange, chain).block();

        assertTrue(chainCalled.get());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("filter：公网直连 /actuator 返回 403 且不进入后续链")
    void shouldRejectPublicDirect() {
        MockServerWebExchange exchange = buildExchange("/actuator/prometheus", "203.0.113.9", null);

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        assertEquals(EXPECTED_403_BODY, exchange.getResponse().getBodyAsString().block());
        assertFalse(chainCalled.get());
    }

    @Test
    @DisplayName("filter：内网代理 + 公网 X-Forwarded-For（Nginx 场景）返回 403")
    void shouldRejectPublicClientBehindProxy() {
        MockServerWebExchange exchange = buildExchange("/actuator/env", "10.1.2.3", "203.0.113.9, 10.1.2.3");

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        assertFalse(chainCalled.get());
    }

    @Test
    @DisplayName("filter：非 actuator 路径不受来源限制")
    void shouldIgnoreNonActuatorPath() {
        MockServerWebExchange exchange = buildExchange("/auth/v1/login", "203.0.113.9", null);

        filter.filter(exchange, chain).block();

        assertTrue(chainCalled.get());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("filter：X-Forwarded-For 非 IP 字面量按不可信处理（不做 DNS 解析）")
    void shouldRejectHostnameForwardedFor() {
        MockServerWebExchange exchange = buildExchange("/actuator/health", "10.1.2.3", "evil.example.com");

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        assertFalse(chainCalled.get());
    }

    @Test
    @DisplayName("filter：关闭 internal-only 后公网来源放行")
    void shouldAllowWhenDisabled() {
        properties.getActuator().setInternalOnly(false);
        MockServerWebExchange exchange = buildExchange("/actuator/prometheus", "203.0.113.9", null);

        filter.filter(exchange, chain).block();

        assertTrue(chainCalled.get());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("getOrder：先于治理端点令牌校验")
    void shouldRunBeforeGovernanceFilter() {
        assertTrue(filter.getOrder() < new ActuatorGovernanceAuthWebFilter(properties).getOrder());
    }
}
