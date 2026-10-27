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

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ActuatorGovernanceAuthWebFilter} 单元测试（P1-13）。
 *
 * <p>覆盖：令牌未配置 fail-closed、缺头/错头 403、正确令牌放行 GET/POST、
 * 非治理端点与相似前缀不被拦截、403 响应体与 TraceId 透传。</p>
 *
 * @author BlueWhale
 */
class ActuatorGovernanceAuthWebFilterTest {

    private static final String TOKEN = "governance-token-0123456789";
    private static final String GOVERNANCE_URI = "http://localhost/actuator/routeGovernance";
    private static final String TRACE_ID = "trace-p1-13";

    private WhaleGatewayProperties properties;
    private ActuatorGovernanceAuthWebFilter filter;
    private final AtomicBoolean chainCalled = new AtomicBoolean(false);
    private final WebFilterChain chain = exchange -> {
        chainCalled.set(true);
        return Mono.empty();
    };

    @BeforeEach
    void setUp() {
        properties = new WhaleGatewayProperties();
        filter = new ActuatorGovernanceAuthWebFilter(properties);
        chainCalled.set(false);
    }

    @Test
    @DisplayName("令牌未配置：治理端点一律 403（fail-closed），不进入后续链")
    void shouldRejectWhenTokenNotConfigured() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(GOVERNANCE_URI).build());

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        assertTrue(!chainCalled.get(), "未授权请求不得进入后续过滤器链");
    }

    @Test
    @DisplayName("缺少 X-Governance-Token：403")
    void shouldRejectWhenHeaderMissing() {
        properties.getGovernance().setToken(TOKEN);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(GOVERNANCE_URI).build());

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        assertTrue(!chainCalled.get());
    }

    @Test
    @DisplayName("令牌不匹配：403")
    void shouldRejectWhenTokenMismatched() {
        properties.getGovernance().setToken(TOKEN);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(GOVERNANCE_URI)
                        .header(ActuatorGovernanceAuthWebFilter.GOVERNANCE_TOKEN_HEADER, "wrong-token")
                        .build());

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        assertTrue(!chainCalled.get());
    }

    @Test
    @DisplayName("写操作（POST）携带正确令牌：放行")
    void shouldPassWriteOperationWithValidToken() {
        properties.getGovernance().setToken(TOKEN);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post(GOVERNANCE_URI)
                        .header(ActuatorGovernanceAuthWebFilter.GOVERNANCE_TOKEN_HEADER, TOKEN)
                        .build());

        filter.filter(exchange, chain).block();

        assertTrue(chainCalled.get());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("读操作（GET）携带正确令牌：放行")
    void shouldPassReadOperationWithValidToken() {
        properties.getGovernance().setToken(TOKEN);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(GOVERNANCE_URI)
                        .header(ActuatorGovernanceAuthWebFilter.GOVERNANCE_TOKEN_HEADER, TOKEN)
                        .build());

        filter.filter(exchange, chain).block();

        assertTrue(chainCalled.get());
    }

    @Test
    @DisplayName("非治理端点（health）：不校验令牌直接放行")
    void shouldPassObservabilityEndpointWithoutToken() {
        properties.getGovernance().setToken(TOKEN);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/actuator/health").build());

        filter.filter(exchange, chain).block();

        assertTrue(chainCalled.get());
    }

    @Test
    @DisplayName("相似前缀（/actuator/routeGovernanceX）：不误拦截")
    void shouldNotInterceptSimilarPrefix() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/actuator/routeGovernanceX").build());

        filter.filter(exchange, chain).block();

        assertTrue(chainCalled.get());
    }

    @Test
    @DisplayName("403 响应：JSON 体包含 403 业务码并透传 TraceId")
    void shouldWriteForbiddenBodyWithTraceId() {
        properties.getGovernance().setToken(TOKEN);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(GOVERNANCE_URI)
                        .header(TraceIdGlobalFilter.TRACE_ID_HEADER, TRACE_ID)
                        .build());

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        assertEquals(TRACE_ID,
                exchange.getResponse().getHeaders().getFirst(TraceIdGlobalFilter.TRACE_ID_HEADER));
        String body = exchange.getResponse().getBodyAsString().block();
        assertTrue(body != null && body.contains("\"code\":403"), "响应体应包含业务码 403");
    }
}
