package com.bluewhale.gateway.filter;

import com.bluewhale.gateway.common.GatewayResponse;
import com.bluewhale.gateway.config.InternalApiDenyProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 内网接口屏蔽全局过滤器（T6）。
 *
 * <p>对 {@code whale.gateway.internal-deny.patterns} 列的路径模式（Ant 通配）实施外部访问拦截：
 * 命中即直接短路写出 HTTP 403 与与业务侧 {@code Result} 同结构的 JSON 体，不进入下游过滤器链
 * （写法对齐 {@code RateLimitGlobalFilter} 的 429 短路，并透传链路追踪头）。</p>
 *
 * <p>过滤器顺序置于鉴权（{@code JwtAuthGlobalFilter} order=-100）之前，确保内网路径即使未携带
 * 合法令牌也直接以 403 拒绝，而非回落为 401。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class InternalApiDenyGlobalFilter implements GlobalFilter, Ordered {

    /** 内网接口拦截时的业务状态码 */
    private static final int DENY_CODE = 403;

    /** 内网接口拦截时的提示文案 */
    private static final String DENY_MESSAGE = "内网接口不允许外部访问";

    /** JSON 响应内容类型 */
    private static final String JSON_CONTENT_TYPE = "application/json;charset=UTF-8";

    /** 过滤器顺序：早于鉴权（-100）与权限码头（-50），保证内网路径先被拒绝 */
    private static final int ORDER = -110;

    private final InternalApiDenyProperties properties;

    /** Ant 路径匹配器（线程安全，复用单例） */
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * 构造内网接口屏蔽过滤器
     *
     * @param properties 内网接口屏蔽配置
     */
    public InternalApiDenyGlobalFilter(InternalApiDenyProperties properties) {
        this.properties = properties;
    }

    /**
     * 内网路径拦截：命中配置的路径模式时短路返回 403，否则放行
     *
     * @param exchange 当前请求上下文
     * @param chain    网关过滤器链
     * @return 响应写出的完成信号
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }
        String path = exchange.getRequest().getPath().value();
        if (isDenied(path)) {
            log.warn("内网接口拒绝外部访问: path={}", path);
            return denied(exchange);
        }
        return chain.filter(exchange);
    }

    /**
     * 判断请求路径是否命中内网接口屏蔽模式
     *
     * @param path 请求路径
     * @return 命中任一模式返回 {@code true}
     */
    private boolean isDenied(String path) {
        List<String> patterns = properties.getPatterns();
        if (patterns == null || patterns.isEmpty()) {
            return false;
        }
        for (String pattern : patterns) {
            if (pattern != null && pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 写出 403 内网接口拒绝响应（与业务侧 Result 同结构 JSON 体），并透传链路追踪头
     *
     * @param exchange 当前请求上下文
     * @return 响应写出完成的信号，不进入下游过滤器链
     */
    private Mono<Void> denied(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.parseMediaType(JSON_CONTENT_TYPE));
        String traceId = exchange.getRequest().getHeaders().getFirst(TraceIdGlobalFilter.TRACE_ID_HEADER);
        if (traceId != null && !traceId.isBlank()) {
            response.getHeaders().set(TraceIdGlobalFilter.TRACE_ID_HEADER, traceId);
        }
        String body = GatewayResponse.of(DENY_CODE, DENY_MESSAGE).toJson();
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
