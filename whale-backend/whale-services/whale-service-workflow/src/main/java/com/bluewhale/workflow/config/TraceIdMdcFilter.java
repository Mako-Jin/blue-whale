package com.bluewhale.workflow.config;

import com.bluewhale.common.web.WebHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 链路 ID 日志透传过滤器：把网关注入的 {@code X-Trace-Id} 写入 MDC。
 *
 * <p>日志格式 {@code logging.pattern.level: "%5p [%X{traceId:-}]"} 依赖该 MDC 键输出 traceId，
 * 未携带请求头时保持为空、格式回落为 {@code -}。请求结束无论成败都清理 MDC，避免线程池复用串号。</p>
 *
 * @author BlueWhale
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TraceIdMdcFilter extends OncePerRequestFilter {

    /** MDC 键名，与日志格式中的 {@code %X{traceId}} 一致 */
    public static final String MDC_TRACE_ID = "traceId";

    /**
     * 绑定 traceId 到 MDC，请求结束后清理
     *
     * @param request HTTP 请求
     * @param response HTTP 响应
     * @param filterChain 过滤器链
     * @throws ServletException Servlet 异常
     * @throws IOException IO 异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = WebHeaders.traceId(request);
        if (traceId != null && !traceId.isBlank()) {
            MDC.put(MDC_TRACE_ID, traceId);
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_TRACE_ID);
        }
    }
}
