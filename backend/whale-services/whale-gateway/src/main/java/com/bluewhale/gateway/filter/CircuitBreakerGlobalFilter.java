package com.bluewhale.gateway.filter;

import com.bluewhale.gateway.common.GatewayResponse;
import com.bluewhale.gateway.config.CircuitBreakerProperties;
import lombok.extern.slf4j.Slf4j;
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
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 网关熔断降级全局过滤器（T4）。
 *
 * <p>对 {@code whale.gateway.circuit-breaker.apply-to-routes} 列出的路由实施轻量熔断：
 * 单次下游调用超过 {@code timeoutMillis} 或抛出异常均计为一次失败；最近 {@code slidingWindowSize} 次
 * 调用的失败率达到 {@code failureRateThreshold} 即打开熔断；打开后经
 * {@code waitDurationInOpenStateMillis} 等待期放行一次半开探测，探测成功则恢复闭合、失败则重新打开。</p>
 *
 * <p>熔断打开时或下游调用失败/超时时，直接短路写出 HTTP 503 与与业务侧 {@code Result} 同结构的
 * JSON 体，并透传链路追踪头（写法对齐 {@code RateLimitGlobalFilter} 的 429 短路）。</p>
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

    /** 失败率百分比基数 */
    private static final int PERCENT_BASE = 100;

    private final CircuitBreakerProperties properties;

    /** 路由 id 到熔断状态的映射，按路由维度隔离统计窗口 */
    private final Map<String, RouteCircuitBreaker> breakers = new ConcurrentHashMap<>();

    /**
     * 构造熔断降级过滤器
     *
     * @param properties 网关熔断降级配置
     */
    public CircuitBreakerGlobalFilter(CircuitBreakerProperties properties) {
        this.properties = properties;
    }

    /**
     * 熔断判定：路由不在适用列表或熔断未开启时直接放行；熔断打开时短路返回 503；
     * 否则包裹下游调用，超时或异常计为失败并降级，成功计为一次成功调用。
     *
     * @param exchange 当前请求上下文
     * @param chain    网关过滤器链
     * @return 响应写出的完成信号
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String routeId = resolveRouteId(exchange);
        RouteCircuitBreaker breaker = resolveBreaker(routeId);
        if (breaker == null) {
            return chain.filter(exchange);
        }
        if (!breaker.tryAcquire(System.currentTimeMillis())) {
            log.warn("网关熔断已打开，短路请求并降级: route={}", routeId);
            return unavailable(exchange);
        }
        return chain.filter(exchange)
                .timeout(Duration.ofMillis(properties.getTimeoutMillis()))
                .doOnSuccess(unused -> breaker.record(true, System.currentTimeMillis()))
                .onErrorResume(e -> {
                    breaker.record(false, System.currentTimeMillis());
                    log.warn("下游调用超时或失败，触发网关熔断降级: route={}, reason={}", routeId, e.getMessage());
                    return unavailable(exchange);
                });
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
     * 按路由 id 获取（必要时创建）熔断器，并判断该路由是否参与熔断
     *
     * @param routeId 当前请求命中的路由 id，可能为 {@code null}
     * @return 参与熔断时返回对应熔断器；未开启熔断或路由不在适用列表时返回 {@code null}
     */
    private RouteCircuitBreaker resolveBreaker(String routeId) {
        if (!properties.isEnabled() || routeId == null) {
            return null;
        }
        List<String> applyToRoutes = properties.getApplyToRoutes();
        if (applyToRoutes == null || !applyToRoutes.contains(routeId)) {
            return null;
        }
        return breakers.computeIfAbsent(routeId, id -> new RouteCircuitBreaker(
                properties.getSlidingWindowSize(), properties.getFailureRateThreshold(),
                properties.getWaitDurationInOpenStateMillis()));
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

    /**
     * 单路由熔断状态机：滑动窗口失败率判定 + 打开等待 + 半开探测。
     *
     * <p>以显式时间参数驱动状态迁移，避免依赖真实时钟；所有状态迁移方法均加锁，
     * 保证并发请求下计数与迁移一致。</p>
     *
     * @author BlueWhale
     */
    private static final class RouteCircuitBreaker {

        /** 失败率统计的滑动窗口大小 */
        private final int windowSize;

        /** 触发熔断的失败率阈值（百分比） */
        private final int failureRateThreshold;

        /** 熔断打开后的等待时长（毫秒） */
        private final long waitDurationMillis;

        /** 最近若干次调用的结果（{@code true} 成功、{@code false} 失败），仅保留窗口大小内记录 */
        private final Deque<Boolean> outcomes = new ArrayDeque<>();

        /** 是否处于打开状态 */
        private boolean open;

        /** 是否已有半开探测在途，避免等待期满后放行过量请求 */
        private boolean halfOpenProbeInFlight;

        /** 熔断打开状态的截止时间戳（毫秒），当前时间达到该值即进入半开探测 */
        private long openUntilMillis;

        /**
         * 构造单路由熔断状态机
         *
         * @param windowSize            滑动窗口大小
         * @param failureRateThreshold  失败率阈值（百分比）
         * @param waitDurationMillis    打开后等待时长（毫秒）
         */
        private RouteCircuitBreaker(int windowSize, int failureRateThreshold, long waitDurationMillis) {
            this.windowSize = windowSize;
            this.failureRateThreshold = failureRateThreshold;
            this.waitDurationMillis = waitDurationMillis;
        }

        /**
         * 尝试获取一次调用许可：闭合时放行；打开且未满等待期时拒绝；等待期满后放行一次半开探测
         *
         * @param now 当前时间戳（毫秒）
         * @return 允许调用返回 {@code true}，否则返回 {@code false}
         */
        private synchronized boolean tryAcquire(long now) {
            if (!open) {
                return true;
            }
            if (now < openUntilMillis || halfOpenProbeInFlight) {
                return false;
            }
            halfOpenProbeInFlight = true;
            return true;
        }

        /**
         * 记录一次调用结果：半开探测结果直接决定闭合或重新打开；否则计入滑动窗口并评估失败率
         *
         * @param success 本次调用是否成功
         * @param now     当前时间戳（毫秒）
         */
        private synchronized void record(boolean success, long now) {
            if (halfOpenProbeInFlight) {
                if (success) {
                    close();
                } else {
                    open(now);
                }
                return;
            }
            addOutcome(success);
            evaluate(now);
        }

        /**
         * 将调用结果加入滑动窗口，超出窗口大小时移除最旧记录
         *
         * @param success 本次调用是否成功
         */
        private void addOutcome(boolean success) {
            if (outcomes.size() >= windowSize) {
                outcomes.removeFirst();
            }
            outcomes.addLast(success);
        }

        /**
         * 窗口填满后按失败率评估是否打开熔断
         *
         * @param now 当前时间戳（毫秒）
         */
        private void evaluate(long now) {
            if (outcomes.size() < windowSize) {
                return;
            }
            long failures = outcomes.stream().filter(outcome -> !outcome).count();
            if (failures * PERCENT_BASE / outcomes.size() >= failureRateThreshold) {
                open(now);
            }
        }

        /**
         * 打开熔断并清空统计窗口，设置等待截止时间
         *
         * @param now 当前时间戳（毫秒）
         */
        private void open(long now) {
            open = true;
            halfOpenProbeInFlight = false;
            outcomes.clear();
            openUntilMillis = now + waitDurationMillis;
        }

        /**
         * 关闭熔断并复位全部状态
         */
        private void close() {
            open = false;
            halfOpenProbeInFlight = false;
            outcomes.clear();
            openUntilMillis = 0L;
        }
    }
}
