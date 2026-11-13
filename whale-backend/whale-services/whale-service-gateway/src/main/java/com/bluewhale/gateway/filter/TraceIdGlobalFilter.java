package com.bluewhale.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 链路追踪过滤器：透传入站 X-Trace-Id，缺失或非法则重新生成；请求下游与响应均携带
 *
 * <p>入站 traceId 来自客户端，必须校验：仅放行 1~64 位的字母、数字、{@code -}、{@code _}，
 * 非法值直接丢弃并重新生成，避免超长/含控制字符的 traceId 污染日志与响应头。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class TraceIdGlobalFilter implements GlobalFilter, Ordered {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /** 允许透传的 traceId 格式：1~64 位字母/数字/连字符/下划线 */
    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER);
        if (traceId == null || !TRACE_ID_PATTERN.matcher(traceId).matches()) {
            if (traceId != null && !traceId.isBlank()) {
                log.debug("入站 traceId 非法，已重新生成: {}", traceId.length());
            }
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        String finalTraceId = traceId;
        // 响应头直接写在原 response 上（Spring Framework 7 的 exchange.mutate() 不再支持 response builder）
        exchange.getResponse().getHeaders().set(TRACE_ID_HEADER, finalTraceId);
        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(request -> request.headers(headers -> headers.set(TRACE_ID_HEADER, finalTraceId)))
                .build();
        return chain.filter(mutatedExchange);
    }

    @Override
    public int getOrder() {
        return -200;
    }
}
