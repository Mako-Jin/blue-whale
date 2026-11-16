package com.bluewhale.gateway.filter;

import com.bluewhale.gateway.common.GatewayResponse;
import com.bluewhale.gateway.config.CircuitBreakerProperties;
import com.bluewhale.gateway.support.CircuitAlertNotifier;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 网关熔断降级全局过滤器（T4 / G2）。
 *
 * <p>对 {@code whale.gateway.circuit-breaker.apply-to-routes} 列出的路由，基于 resilience4j
 * {@link CircuitBreaker} 手动状态机（{@code tryAcquirePermission} + {@code onSuccess}/{@code onError}）
 * 实施熔断：单次下游调用超过 {@code timeoutMillis} 或抛出异常均计为一次失败；计数滑动窗口（COUNT_BASED，
 * {@code minimumNumberOfCalls} = 窗口大小）的失败率达到 {@code failureRateThreshold} 即打开熔断；
 * 打开后经 {@code waitDurationInOpenStateMillis} 等待期放行一次半开探测，探测成功恢复闭合、
 * 失败重新打开（由 resilience4j 状态机负责迁移，不再自研）。</p>
 *
 * <p>熔断打开时（resilience4j 拒绝放行）或下游真正不可用（连接失败 / 超时 / IO 异常）时，
 * 短路写出 HTTP 503 与业务侧 {@code Result} 同结构 JSON 体，并透传链路追踪头。
 * <b>非可用性异常（如路由不存在、下游 4xx/5xx 经异常通道抛出）不再改写为 503</b>，
 * 而是继续抛出交给 {@code GatewayExceptionHandler} 统一映射，避免掩盖真实状态码。</p>
 *
 * <p>半开只放行 <b>一次</b> 探测（{@code permittedNumberOfCallsInHalfOpenState(1)}），
 * 探测失败即重新打开——这是「快速止损优先」的取舍：偶发失败会导致多等一个等待期，
 * 但不会把故障流量持续放给下游。</p>
 *
 * <p>GW-04 熔断指标（Micrometer，均带 {@code route} 标签）：{@code gateway.circuit.calls} 记录下游调用
 * 成功/失败（{@code result} 标签），{@code gateway.circuit.short_circuits} 记录熔断打开的短路次数，
 * {@code gateway.circuit.state} 以 0/1/2 反映该路由熔断状态（CLOSED/OPEN/HALF_OPEN，读取 resilience4j
 * 实时状态）。容器内无 {@link MeterRegistry} 时静默跳过，不影响熔断与降级主流程。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class CircuitBreakerGlobalFilter implements GlobalFilter, Ordered {

    /** 熔断降级时的业务状态码 */
    private static final int FALLBACK_CODE = 503;

    /** 熔断降级时的提示文案 */
    private static final String FALLBACK_MESSAGE = "服务暂时不可用，请稍后重试";

    /** JSON 响应内容类型 */
    private static final String JSON_CONTENT_TYPE = "application/json;charset=UTF-8";

    /** 过滤器顺序：晚于鉴权过滤器（-100）、早于业务转发，仅对已认证请求实施熔断 */
    private static final int ORDER = -75;

    /** 下游调用结果计数指标名 */
    private static final String METER_CALLS = "gateway.circuit.calls";

    /** 熔断打开短路计数指标名 */
    private static final String METER_SHORT_CIRCUITS = "gateway.circuit.short_circuits";

    /** 熔断状态指标名 */
    private static final String METER_STATE = "gateway.circuit.state";

    /** 指标路由标签名 */
    private static final String TAG_ROUTE = "route";

    /** 指标调用结果标签名 */
    private static final String TAG_RESULT = "result";

    /** 调用成功的 result 标签值 */
    private static final String RESULT_SUCCESS = "success";

    /** 调用失败的 result 标签值 */
    private static final String RESULT_FAILURE = "failure";

    private final CircuitBreakerProperties properties;

    /** 指标注册表提供者：容器无 MeterRegistry 时返回 {@code null}，指标静默跳过 */
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    /** 熔断状态告警上报客户端（未配置 alert-base-url 时整体跳过，旁路不影响主流程） */
    private final CircuitAlertNotifier alertNotifier;

    /** resilience4j 熔断器注册表（按路由 id 隔离实例） */
    private final CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();

    /**
     * 路由 id 到 resilience4j 熔断器的映射。
     *
     * <p>生命周期与 {@code apply-to-routes} 强绑定：键仅在路由命中该静态列表时才创建，
     * 列表来自本地配置而非动态路由仓库，因此不会随 Nacos 路由增删而无界增长；
     * 每个键的 Gauge 也只在首次创建时注册一次（{@code computeIfAbsent} 保证）。</p>
     *
     * <p><b>运维约定</b>：{@code computeIfAbsent} 只增不减，若热更新从 {@code apply-to-routes}
     * 移除某路由，对应 breaker 与 Gauge 仍会保留到进程重启（低频且无副作用），
     * 因此「移除熔断路由」需重启网关生效。</p>
     */
    private final Map<String, CircuitBreaker> breakers = new ConcurrentHashMap<>();

    /**
     * 构造熔断降级过滤器
     *
     * @param properties            网关熔断降级配置
     * @param meterRegistryProvider Micrometer 指标注册表提供者，缺省时返回 {@code null} 表示不采集指标
     * @param alertNotifier         熔断状态告警上报客户端
     */
    public CircuitBreakerGlobalFilter(CircuitBreakerProperties properties,
                                      ObjectProvider<MeterRegistry> meterRegistryProvider,
                                      CircuitAlertNotifier alertNotifier) {
        this.properties = properties;
        this.meterRegistryProvider = meterRegistryProvider;
        this.alertNotifier = alertNotifier;
    }

    /**
     * 熔断判定：路由不在适用列表或熔断未开启时直接放行；否则经 resilience4j 熔断器执行下游调用，
     * 因熔断打开被拒或调用失败/超时时降级返回 503。
     *
     * @param exchange 当前请求上下文
     * @param chain    网关过滤器链
     * @return 响应写出的完成信号
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String routeId = resolveRouteId(exchange);
        CircuitBreaker breaker = resolveBreaker(routeId);
        if (breaker == null) {
            return chain.filter(exchange);
        }
        if (!breaker.tryAcquirePermission()) {
            log.warn("网关熔断已打开，短路请求并降级: route={}", routeId);
            countShortCircuit(routeId);
            return unavailable(exchange);
        }
        long startNanos = System.nanoTime();
        return chain.filter(exchange)
                .timeout(Duration.ofMillis(Math.max(1, properties.getTimeoutMillis())))
                .doOnSuccess(unused -> {
                    breaker.onSuccess(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
                    countCall(routeId, true);
                })
                .onErrorResume(e -> {
                    breaker.onError(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS, e);
                    countCall(routeId, false);
                    if (!isUnavailable(e)) {
                        // 非可用性异常（路由不存在 / 下游 4xx、5xx 经异常通道抛出等）不降级为 503，
                        // 继续抛出交给 GatewayExceptionHandler 映射，避免把 404 掩盖成 503
                        log.warn("下游调用异常但非不可用，继续交由全局异常处理: route={}, reason={}",
                                routeId, e.toString());
                        return Mono.error(e);
                    }
                    log.warn("下游调用超时或不可用，触发网关熔断降级: route={}, reason={}", routeId, e.getMessage());
                    return unavailable(exchange);
                });
    }

    /**
     * 判断异常是否代表下游「不可用」（连接失败 / 超时 / IO 异常），仅此三类降级为 503。
     *
     * <p>reactor 的 {@code timeout} 操作符发出 {@link java.util.concurrent.TimeoutException}；
     * 连接被拒、连接重置、响应提前关闭等均落在 {@link java.io.IOException} 体系内
     * （如 {@link java.net.ConnectException}、Netty {@code PrematureCloseException}）。
     * 用 {@code Exceptions.unwrap} 剥掉 {@code WebClientRequestException} 等包装层后再判定。</p>
     *
     * @param error 下游调用异常
     * @return 属于下游不可用返回 {@code true}
     */
    private static boolean isUnavailable(Throwable error) {
        Throwable unwrapped = Exceptions.unwrap(error);
        return unwrapped instanceof TimeoutException || unwrapped instanceof IOException;
    }

    /**
     * 解析当前请求命中的网关路由 id
     *
     * @param exchange 当前请求上下文
     * @return 命中路由的 id；未匹配到路由时返回 {@code null}
     */
    private String resolveRouteId(ServerWebExchange exchange) {
        Object attribute = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (!(attribute instanceof Route route)) {
            return null;
        }
        return route.getId();
    }

    /**
     * 按路由 id 获取（必要时创建）resilience4j 熔断器，并判断该路由是否参与熔断
     *
     * @param routeId 当前请求命中的路由 id，可能为 {@code null}
     * @return 参与熔断时返回对应熔断器；未开启熔断或路由不在适用列表时返回 {@code null}
     */
    private CircuitBreaker resolveBreaker(String routeId) {
        if (!properties.isEnabled() || routeId == null) {
            return null;
        }
        List<String> applyToRoutes = properties.getApplyToRoutes();
        if (applyToRoutes == null || !applyToRoutes.contains(routeId)) {
            return null;
        }
        return breakers.computeIfAbsent(routeId, id -> {
            CircuitBreaker breaker = registry.circuitBreaker(id, buildConfig());
            registerStateGauge(id, breaker);
            registerStateListener(id, breaker);
            return breaker;
        });
    }

    /**
     * 注册熔断状态迁移日志（S-07）与告警上报：CLOSED/OPEN/HALF_OPEN 迁移与半开探测结果
     * 可在运行态日志中取证；打开时上报 ERROR 告警、恢复闭合时上报 INFO 恢复告警
     * （whale-alert 侧按指纹去重并触发通知，上报失败只记 WARN 不影响熔断主流程）
     *
     * @param routeId 路由 id
     * @param breaker resilience4j 熔断器
     */
    private void registerStateListener(String routeId, CircuitBreaker breaker) {
        breaker.getEventPublisher().onStateTransition(event -> {
            CircuitBreaker.StateTransition transition = event.getStateTransition();
            log.info("网关熔断状态迁移: route={}, {} -> {}", routeId,
                    transition.getFromState(), transition.getToState());
            if (transition.getToState() == CircuitBreaker.State.OPEN) {
                alertNotifier.notifyOpenAsync(routeId);
            } else if (transition.getToState() == CircuitBreaker.State.CLOSED) {
                alertNotifier.notifyRecoveredAsync(routeId);
            }
        });
    }

    /**
     * 由网关配置构造 resilience4j 熔断配置（计数滑动窗口，窗口即最小调用数）。
     *
     * <p>对配置做下界/上界收敛，避免误配（如 0 窗口、0 超时、负阈值）导致 resilience4j
     * 构造异常或熔断行为失真。</p>
     *
     * @return resilience4j 熔断配置
     */
    private CircuitBreakerConfig buildConfig() {
        int windowSize = Math.max(1, properties.getSlidingWindowSize());
        int failureRate = Math.min(100, Math.max(1, properties.getFailureRateThreshold()));
        return CircuitBreakerConfig.custom()
                .slidingWindowType(SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(windowSize)
                .minimumNumberOfCalls(windowSize)
                .failureRateThreshold(failureRate)
                .waitDurationInOpenState(Duration.ofMillis(
                        Math.max(1, properties.getWaitDurationInOpenStateMillis())))
                .permittedNumberOfCallsInHalfOpenState(1)
                .build();
    }

    /**
     * 获取容器内的 Micrometer 指标注册表
     *
     * @return 存在则返回指标注册表；不存在返回 {@code null}，调用方据此静默跳过指标采集
     */
    private MeterRegistry resolveRegistry() {
        return meterRegistryProvider.getIfAvailable();
    }

    /**
     * 首次为路由创建熔断器时注册状态 Gauge（值域 0=CLOSED、1=OPEN、2=HALF_OPEN）
     *
     * @param routeId 路由 id
     * @param breaker resilience4j 熔断器
     */
    private void registerStateGauge(String routeId, CircuitBreaker breaker) {
        MeterRegistry meterRegistry = resolveRegistry();
        if (meterRegistry == null) {
            return;
        }
        Gauge.builder(METER_STATE, breaker, CircuitBreakerGlobalFilter::stateCode)
                .tag(TAG_ROUTE, routeId)
                .description("网关单路由熔断状态：0=CLOSED，1=OPEN，2=HALF_OPEN")
                .register(meterRegistry);
    }

    /**
     * resilience4j 熔断状态映射为指标值
     *
     * @param breaker resilience4j 熔断器
     * @return 0=CLOSED、1=OPEN、2=HALF_OPEN（DISABLED/METRICS_ONLY 归为 0）
     */
    private static int stateCode(CircuitBreaker breaker) {
        return switch (breaker.getState()) {
            case OPEN -> 1;
            case HALF_OPEN -> 2;
            default -> 0;
        };
    }

    /**
     * 记录一次下游调用结果计数（成功或失败）
     *
     * @param routeId 路由 id
     * @param success 本次调用是否成功
     */
    private void countCall(String routeId, boolean success) {
        MeterRegistry meterRegistry = resolveRegistry();
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.counter(METER_CALLS, TAG_ROUTE, routeId, TAG_RESULT, success ? RESULT_SUCCESS : RESULT_FAILURE)
                .increment();
    }

    /**
     * 记录一次因熔断打开导致的短路计数
     *
     * @param routeId 路由 id
     */
    private void countShortCircuit(String routeId) {
        MeterRegistry meterRegistry = resolveRegistry();
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.counter(METER_SHORT_CIRCUITS, TAG_ROUTE, routeId).increment();
    }

    /**
     * 写出 503 熔断降级响应（与业务侧 Result 同结构 JSON 体），并透传链路追踪头；
     * 若响应已提交则不再覆盖，仅结束请求。
     *
     * @param exchange 当前请求上下文
     * @return 响应写出完成的信号
     */
    private Mono<Void> unavailable(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            log.warn("响应已提交，无法写出熔断降级响应体");
            return Mono.empty();
        }
        response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        response.getHeaders().setContentType(MediaType.parseMediaType(JSON_CONTENT_TYPE));
        String traceId = exchange.getRequest().getHeaders().getFirst(TraceIdGlobalFilter.TRACE_ID_HEADER);
        if (traceId != null && !traceId.isBlank()) {
            response.getHeaders().set(TraceIdGlobalFilter.TRACE_ID_HEADER, traceId);
        }
        String body = GatewayResponse.of(FALLBACK_CODE, FALLBACK_MESSAGE).toJson();
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
