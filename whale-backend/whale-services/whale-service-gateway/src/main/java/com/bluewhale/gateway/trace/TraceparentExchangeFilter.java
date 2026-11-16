package com.bluewhale.gateway.trace;

import com.bluewhale.security.contract.GatewayContract;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;

/**
 * 出站 W3C {@code traceparent} 注入。
 *
 * <p>网关到 auth（权限码头回落、白名单/黑名单、路由覆盖）与 whale-alert（熔断告警）的调用都是
 * 内网 WebClient 调用。仅靠自动埋点依赖 WebClient 的 observation 装配与 Reactor 上下文传播，
 * 在这些手工构造的 WebClient 上并不稳定；显式把当前 span 的 traceId/spanId 写入
 * {@code traceparent} 头，保证下游能接着网关的 span，链路不再在网关处断头。</p>
 *
 * @author BlueWhale
 */
public final class TraceparentExchangeFilter {

    /** traceparent 版本号（W3C Trace Context Level 1） */
    private static final String VERSION = "00";

    /** 采样标记：已采样 */
    private static final String SAMPLED = "01";

    /** 采样标记：未采样 */
    private static final String NOT_SAMPLED = "00";

    /** traceparent 分段分隔符 */
    private static final String SEPARATOR = "-";

    /**
     * 私有构造器：工具类，禁止实例化
     */
    private TraceparentExchangeFilter() {
    }

    /**
     * 创建注入 traceparent 的过滤器
     *
     * @param tracerProvider 追踪器提供者（无 Micrometer Tracing 时为空）
     * @return ExchangeFilterFunction
     */
    public static ExchangeFilterFunction create(ObjectProvider<Tracer> tracerProvider) {
        return (request, next) -> {
            Tracer tracer = tracerProvider.getIfAvailable();
            String traceparent = traceparent(tracer);
            if (traceparent == null) {
                return next.exchange(request);
            }
            ClientRequest mutated = ClientRequest.from(request)
                    .headers(headers -> headers.set(GatewayContract.Headers.TRACEPARENT, traceparent))
                    .build();
            return next.exchange(mutated);
        };
    }

    /**
     * 读取当前 span 并格式化为 traceparent
     *
     * @param tracer 追踪器，可为 {@code null}
     * @return traceparent；无活动 span 时返回 {@code null}
     */
    public static String traceparent(Tracer tracer) {
        if (tracer == null) {
            return null;
        }
        Span span = tracer.currentSpan();
        return span == null ? null : traceparent(span.context());
    }

    /**
     * 把 span 上下文格式化为 W3C traceparent
     *
     * @param context span 上下文
     * @return traceparent 字符串
     */
    public static String traceparent(TraceContext context) {
        if (context == null || context.traceId() == null || context.spanId() == null) {
            return null;
        }
        String sampled = Boolean.TRUE.equals(context.sampled()) ? SAMPLED : NOT_SAMPLED;
        return String.join(SEPARATOR, VERSION, context.traceId(), context.spanId(), sampled);
    }
}
