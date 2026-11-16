package com.bluewhale.gateway.filter;

import com.bluewhale.gateway.trace.TraceparentExchangeFilter;
import com.bluewhale.security.contract.GatewayContract;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 链路追踪过滤器：业务追踪号（{@code X-Trace-Id}）与 W3C 分布式追踪并存。
 *
 * <p>取值优先级：</p>
 * <ol>
 *   <li>入站 {@code X-Trace-Id}（校验后透传，保持既有客户端/日志口径）；</li>
 *   <li>Micrometer Tracing 当前 span 的 traceId（OTel 自动埋 ServerWebExchange 后即存在）；</li>
 *   <li>本地生成 32 位随机串。</li>
 * </ol>
 *
 * <p>入站 traceId 来自客户端，必须校验：仅放行 1~64 位的字母、数字、{@code -}、{@code _}，
 * 非法值直接丢弃并重新生成，避免超长/含控制字符的 traceId 污染日志与响应头。</p>
 *
 * <p>若当前存在 OTel span，还会向下游写入 {@code traceparent}，把网关 span 作为下游的父 span；
 * 客户端已带合法 {@code traceparent} 时，服务端观测会沿用同一 traceId，两者自然对齐。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class TraceIdGlobalFilter implements GlobalFilter, Ordered {

    /** 业务追踪号头名（与跨服务契约保持一致） */
    public static final String TRACE_ID_HEADER = GatewayContract.Headers.TRACE_ID;

    /** 允许透传的 traceId 格式：1~64 位字母/数字/连字符/下划线 */
    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    /** 追踪器提供者：未引入 Micrometer Tracing 时为空，退化为纯业务追踪号 */
    private final ObjectProvider<Tracer> tracerProvider;

    /**
     * 构造链路追踪过滤器
     *
     * @param tracerProvider 追踪器提供者
     */
    public TraceIdGlobalFilter(ObjectProvider<Tracer> tracerProvider) {
        this.tracerProvider = tracerProvider;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Tracer tracer = tracerProvider.getIfAvailable();
        Span span = tracer == null ? null : tracer.currentSpan();
        String traceId = resolveTraceId(exchange, span);
        String traceparent = span == null ? null : TraceparentExchangeFilter.traceparent(span.context());
        // 响应头直接写在原 response 上（Spring Framework 7 的 exchange.mutate() 不再支持 response builder）
        exchange.getResponse().getHeaders().set(TRACE_ID_HEADER, traceId);
        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(request -> request.headers(headers -> {
                    headers.set(TRACE_ID_HEADER, traceId);
                    if (traceparent != null) {
                        headers.set(GatewayContract.Headers.TRACEPARENT, traceparent);
                    }
                }))
                .build();
        return chain.filter(mutatedExchange);
    }

    /**
     * 解析最终使用的业务追踪号
     *
     * @param exchange 当前请求
     * @param span     当前 OTel span，可为 {@code null}
     * @return 合法 traceId
     */
    private static String resolveTraceId(ServerWebExchange exchange, Span span) {
        String inbound = exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER);
        if (inbound != null && TRACE_ID_PATTERN.matcher(inbound).matches()) {
            return inbound;
        }
        if (inbound != null && !inbound.isBlank()) {
            log.debug("入站 traceId 非法，已重新生成: length={}", inbound.length());
        }
        if (span != null) {
            String spanTraceId = span.context().traceId();
            if (spanTraceId != null && TRACE_ID_PATTERN.matcher(spanTraceId).matches()) {
                return spanTraceId;
            }
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    @Override
    public int getOrder() {
        return -200;
    }
}
