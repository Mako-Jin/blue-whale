package com.bluewhale.gateway.filter;

import com.bluewhale.gateway.config.CircuitBreakerProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
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
 * {@link CircuitBreakerGlobalFilter} 单元测试（T4）：放行、降级、超时、熔断打开短路、半开探测恢复与开关；
 * 并覆盖 GW-04 熔断指标（calls / short_circuits 计数与 state 状态 Gauge）。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class CircuitBreakerGlobalFilterTest {

    private static final String PROTECTED_ROUTE = "whale-auth";
    private static final String OTHER_ROUTE = "whale-pay";
    private static final String PATH = "http://localhost/auth/login";
    private static final String METER_CALLS = "gateway.circuit.calls";
    private static final String METER_SHORT_CIRCUITS = "gateway.circuit.short_circuits";
    private static final String METER_STATE = "gateway.circuit.state";
    private static final String TAG_ROUTE = "route";
    private static final String RESULT_SUCCESS = "success";
    private static final String RESULT_FAILURE = "failure";
    private static final String EXPECTED_503_BODY =
            "{\"code\":503,\"message\":\"服务暂时不可用，请稍后重试\",\"data\":null}";

    @Mock
    private GatewayFilterChain chain;

    private CircuitBreakerProperties properties;

    private MeterRegistry registry;

    private CircuitBreakerGlobalFilter filter;

    @BeforeEach
    void setUp() {
        properties = new CircuitBreakerProperties();
        registry = new SimpleMeterRegistry();
        filter = new CircuitBreakerGlobalFilter(properties, new StubMeterProvider(registry));
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

    /**
     * 读取指定路由的调用结果计数，计数器不存在时返回 0
     *
     * @param routeId 路由 id
     * @param result  结果标签值（success / failure）
     * @return 计数器当前累计值
     */
    private double callCount(String routeId, String result) {
        Counter counter = registry.find(METER_CALLS).tag(TAG_ROUTE, routeId).tag("result", result).counter();
        return counter == null ? 0.0d : counter.count();
    }

    /**
     * 读取指定路由的熔断短路计数，计数器不存在时返回 0
     *
     * @param routeId 路由 id
     * @return 计数器当前累计值
     */
    private double shortCircuitCount(String routeId) {
        Counter counter = registry.find(METER_SHORT_CIRCUITS).tag(TAG_ROUTE, routeId).counter();
        return counter == null ? 0.0d : counter.count();
    }

    /**
     * 读取指定路由的熔断状态 Gauge 当前值，Gauge 未注册时返回 -1
     *
     * @param routeId 路由 id
     * @return 0=CLOSED、1=OPEN、2=HALF_OPEN
     */
    private int stateValue(String routeId) {
        Gauge gauge = registry.find(METER_STATE).tag(TAG_ROUTE, routeId).gauge();
        return gauge == null ? -1 : (int) gauge.value();
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
    void shouldRecoverAfterHalfOpenProbeSucceeds() throws InterruptedException {
        properties.setSlidingWindowSize(2);
        properties.setFailureRateThreshold(50);
        properties.setWaitDurationInOpenStateMillis(1);
        when(chain.filter(any())).thenReturn(Mono.error(new RuntimeException("down")));

        filter.filter(buildExchange(PROTECTED_ROUTE), chain).block();
        filter.filter(buildExchange(PROTECTED_ROUTE), chain).block();
        Thread.sleep(5);

        when(chain.filter(any())).thenReturn(Mono.empty());
        MockServerWebExchange probe = buildExchange(PROTECTED_ROUTE);
        filter.filter(probe, chain).block();
        MockServerWebExchange afterClose = buildExchange(PROTECTED_ROUTE);
        filter.filter(afterClose, chain).block();

        assertNull(probe.getResponse().getStatusCode(), "半开探测成功不应降级");
        verify(chain, times(4)).filter(any());
    }

    @Test
    @DisplayName("GW-04 指标：下游成功计数 +1、状态 Gauge 保持 CLOSED(0)、短路计数为 0")
    void shouldCountSuccessCallAndClosedGauge() {
        MockServerWebExchange exchange = buildExchange(PROTECTED_ROUTE);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        assertEquals(1.0d, callCount(PROTECTED_ROUTE, RESULT_SUCCESS));
        assertEquals(0.0d, callCount(PROTECTED_ROUTE, RESULT_FAILURE));
        assertEquals(0.0d, shortCircuitCount(PROTECTED_ROUTE));
        assertEquals(0, stateValue(PROTECTED_ROUTE));
    }

    @Test
    @DisplayName("GW-04 指标：下游失败计数 +1、状态 Gauge 仍为 CLOSED(0)")
    void shouldCountFailureCall() {
        MockServerWebExchange exchange = buildExchange(PROTECTED_ROUTE);
        when(chain.filter(any())).thenReturn(Mono.error(new RuntimeException("down")));

        filter.filter(exchange, chain).block();

        assertEquals(1.0d, callCount(PROTECTED_ROUTE, RESULT_FAILURE));
        assertEquals(0.0d, callCount(PROTECTED_ROUTE, RESULT_SUCCESS));
        assertEquals(0, stateValue(PROTECTED_ROUTE));
    }

    @Test
    @DisplayName("GW-04 指标：熔断打开后短路计数 +1、状态 Gauge 为 OPEN(1)")
    void shouldCountShortCircuitAndOpenGauge() {
        properties.setSlidingWindowSize(2);
        properties.setFailureRateThreshold(50);
        properties.setWaitDurationInOpenStateMillis(60000);
        when(chain.filter(any())).thenReturn(Mono.error(new RuntimeException("down")));

        filter.filter(buildExchange(PROTECTED_ROUTE), chain).block();
        filter.filter(buildExchange(PROTECTED_ROUTE), chain).block();
        filter.filter(buildExchange(PROTECTED_ROUTE), chain).block();

        assertEquals(1.0d, shortCircuitCount(PROTECTED_ROUTE));
        assertEquals(1, stateValue(PROTECTED_ROUTE));
    }

    @Test
    @DisplayName("GW-04 指标：半开探测在途时状态 Gauge 为 HALF_OPEN(2)，探测成功恢复 CLOSED(0)")
    void shouldReportHalfOpenGaugeDuringProbe() throws InterruptedException {
        properties.setSlidingWindowSize(2);
        properties.setFailureRateThreshold(50);
        properties.setWaitDurationInOpenStateMillis(1);
        when(chain.filter(any())).thenReturn(Mono.error(new RuntimeException("down")));

        filter.filter(buildExchange(PROTECTED_ROUTE), chain).block();
        filter.filter(buildExchange(PROTECTED_ROUTE), chain).block();
        assertEquals(1, stateValue(PROTECTED_ROUTE), "等待期内应为 OPEN");
        Thread.sleep(5);

        int[] halfOpenState = new int[1];
        when(chain.filter(any())).thenReturn(Mono.fromRunnable(
                () -> halfOpenState[0] = stateValue(PROTECTED_ROUTE)).then());
        filter.filter(buildExchange(PROTECTED_ROUTE), chain).block();

        assertEquals(2, halfOpenState[0], "半开探测在途应为 HALF_OPEN");
        assertEquals(0, stateValue(PROTECTED_ROUTE), "探测成功后应恢复 CLOSED");
    }

    @Test
    @DisplayName("GW-04 指标：容器内无 MeterRegistry 时静默跳过指标，熔断主流程不受影响")
    void shouldSkipMetricsWhenNoRegistry() {
        CircuitBreakerGlobalFilter noMetricFilter =
                new CircuitBreakerGlobalFilter(properties, new StubMeterProvider(null));
        MockServerWebExchange exchange = buildExchange(PROTECTED_ROUTE);
        when(chain.filter(any())).thenReturn(Mono.empty());

        noMetricFilter.filter(exchange, chain).block();

        verify(chain).filter(any());
        assertNull(exchange.getResponse().getStatusCode());
    }

    /**
     * 测试用 {@link ObjectProvider} 桩：{@code getIfAvailable()} 返回给定的指标注册表
     * （传入 {@code null} 即模拟容器内不存在 MeterRegistry 的场景）。
     *
     * @author BlueWhale
     */
    private static final class StubMeterProvider implements ObjectProvider<MeterRegistry> {

        /** 待返回的指标注册表，可为 {@code null} */
        private final MeterRegistry registry;

        /**
         * 构造指标注册表提供者桩
         *
         * @param registry 待返回的指标注册表，{@code null} 表示不存在
         */
        private StubMeterProvider(MeterRegistry registry) {
            this.registry = registry;
        }

        @Override
        public MeterRegistry getIfAvailable() {
            return registry;
        }
    }
}
