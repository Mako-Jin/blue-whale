package com.bluewhale.gateway.filter;

import com.bluewhale.gateway.config.CircuitBreakerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link CircuitBreakerGlobalFilter} 单元测试（T4）：放行、降级、超时、熔断打开短路、半开探测恢复与开关。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class CircuitBreakerGlobalFilterTest {

    private static final String PROTECTED_ROUTE = "whale-auth";
    private static final String OTHER_ROUTE = "whale-pay";
    private static final String PATH = "http://localhost/auth/login";
    private static final String EXPECTED_503_BODY =
            "{\"code\":503,\"message\":\"服务暂时不可用，请稍后重试\",\"data\":null}";

    @Mock
    private GatewayFilterChain chain;

    private CircuitBreakerProperties properties;

    private CircuitBreakerGlobalFilter filter;

    @BeforeEach
    void setUp() {
        properties = new CircuitBreakerProperties();
        filter = new CircuitBreakerGlobalFilter(properties);
    }

    /**
     * 构造命中指定路由的模拟请求上下文
     *
     * @param routeId 命中的路由 id
     * @return 模拟请求上下文
     */
    private static MockServerWebExchange buildExchange(String routeId) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(PATH).build());
        Route route = Route.async().id(routeId).uri("lb://" + routeId).predicate(e -> true).build();
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, route);
        return exchange;
    }

    @Test
    @DisplayName("filter：开关关闭时直接放行")
    void shouldPassThroughWhenDisabled() {
        properties.setEnabled(false);
        MockServerWebExchange exchange = buildExchange(PROTECTED_ROUTE);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("filter：路由不在 apply-to-routes 时直接放行")
    void shouldPassThroughWhenRouteNotApplied() {
        MockServerWebExchange exchange = buildExchange(OTHER_ROUTE);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("filter：下游正常返回时放行且不降级")
    void shouldPassThroughWhenDownstreamSucceeds() {
        MockServerWebExchange exchange = buildExchange(PROTECTED_ROUTE);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("filter：下游异常时计为失败并返回 503 降级体")
    void shouldReturnServiceUnavailableWhenDownstreamFails() {
        MockServerWebExchange exchange = buildExchange(PROTECTED_ROUTE);
        when(chain.filter(any())).thenReturn(Mono.error(new RuntimeException("connection refused")));

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.getResponse().getStatusCode());
        assertEquals(EXPECTED_503_BODY, exchange.getResponse().getBodyAsString().block());
    }

    @Test
    @DisplayName("filter：下游超时（超过 timeout-millis）时返回 503 降级体")
    void shouldReturnServiceUnavailableWhenTimeout() {
        properties.setTimeoutMillis(40);
        MockServerWebExchange exchange = buildExchange(PROTECTED_ROUTE);
        when(chain.filter(any())).thenReturn(Mono.never());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("filter：连续失败达阈值打开熔断后，后续请求短路返回 503 且不再进入下游")
    void shouldShortCircuitWhenBreakerOpen() {
        properties.setSlidingWindowSize(2);
        properties.setFailureRateThreshold(50);
        properties.setWaitDurationInOpenStateMillis(60000);
        when(chain.filter(any())).thenReturn(Mono.error(new RuntimeException("down")));

        filter.filter(buildExchange(PROTECTED_ROUTE), chain).block();
        filter.filter(buildExchange(PROTECTED_ROUTE), chain).block();
        MockServerWebExchange third = buildExchange(PROTECTED_ROUTE);
        filter.filter(third, chain).block();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, third.getResponse().getStatusCode());
        verify(chain, times(2)).filter(any());
    }

    @Test
    @DisplayName("filter：等待期满后放行一次半开探测，探测成功即恢复闭合")
    void shouldRecoverAfterHalfOpenProbeSucceeds() {
        properties.setSlidingWindowSize(2);
        properties.setFailureRateThreshold(50);
        properties.setWaitDurationInOpenStateMillis(0);
        when(chain.filter(any())).thenReturn(Mono.error(new RuntimeException("down")));

        filter.filter(buildExchange(PROTECTED_ROUTE), chain).block();
        filter.filter(buildExchange(PROTECTED_ROUTE), chain).block();

        when(chain.filter(any())).thenReturn(Mono.empty());
        MockServerWebExchange probe = buildExchange(PROTECTED_ROUTE);
        filter.filter(probe, chain).block();
        MockServerWebExchange afterClose = buildExchange(PROTECTED_ROUTE);
        filter.filter(afterClose, chain).block();

        assertNull(probe.getResponse().getStatusCode(), "半开探测成功不应降级");
        verify(chain, times(4)).filter(any());
    }
}
