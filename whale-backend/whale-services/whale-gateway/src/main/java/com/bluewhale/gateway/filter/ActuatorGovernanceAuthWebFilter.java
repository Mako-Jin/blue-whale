package com.bluewhale.gateway.filter;

import com.bluewhale.gateway.common.GatewayResponse;
import com.bluewhale.gateway.config.WhaleGatewayProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 路由治理端点鉴权过滤器（P1-13）。
 *
 * <p>{@code /actuator/**} 在 {@code JwtAuthGlobalFilter} 白名单内，且 actuator 端点不经过网关路由链，
 * 因此 {@code /actuator/routeGovernance} 的读/写操作（含路由回滚）默认处于匿名可达状态。
 * 本过滤器在 WebFlux 全局链最前置拦截该端点，要求 {@code X-Governance-Token} 与
 * {@code whale.gateway.governance.token} 一致（常量时间比较）；令牌未配置时一律 403（fail-closed）。</p>
 *
 * <p>仅拦截治理端点，health/info/prometheus 等只读观测端点保持匿名可用。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActuatorGovernanceAuthWebFilter implements WebFilter, Ordered {

    /** 治理端点令牌请求头 */
    public static final String GOVERNANCE_TOKEN_HEADER = "X-Governance-Token";

    /** 受保护的治理端点路径 */
    private static final String GOVERNANCE_PATH = "/actuator/routeGovernance";

    private final WhaleGatewayProperties properties;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        if (!GOVERNANCE_PATH.equals(path) && !path.startsWith(GOVERNANCE_PATH + "/")) {
            return chain.filter(exchange);
        }
        String expected = properties.getGovernance().getToken();
        String provided = request.getHeaders().getFirst(GOVERNANCE_TOKEN_HEADER);
        if (expected == null || expected.isBlank() || provided == null
                || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                        provided.getBytes(StandardCharsets.UTF_8))) {
            log.warn("路由治理端点令牌校验失败，拒绝访问: path={}, method={}",
                    path, request.getMethod());
            return forbidden(exchange);
        }
        return chain.filter(exchange);
    }

    /**
     * 返回 403 JSON 响应，并透传链路追踪头
     *
     * @param exchange 当前请求上下文
     * @return 写出响应完成的信号
     */
    private Mono<Void> forbidden(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.parseMediaType("application/json;charset=UTF-8"));
        String traceId = exchange.getRequest().getHeaders().getFirst(TraceIdGlobalFilter.TRACE_ID_HEADER);
        if (traceId != null && !traceId.isBlank()) {
            response.getHeaders().set(TraceIdGlobalFilter.TRACE_ID_HEADER, traceId);
        }
        String body = GatewayResponse.of(403, "路由治理端点拒绝访问").toJson();
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
