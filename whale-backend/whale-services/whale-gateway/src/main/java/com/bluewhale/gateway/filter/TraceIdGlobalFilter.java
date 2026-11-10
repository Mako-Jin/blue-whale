package com.bluewhale.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 链路追踪过滤器：透传入站 X-Trace-Id，缺失则生成；请求下游与响应均携带
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class TraceIdGlobalFilter implements GlobalFilter, Ordered {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
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
