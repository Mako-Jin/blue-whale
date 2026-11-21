package com.bluewhale.alert.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Alertmanager 入站请求体大小限制过滤器。
 *
 * <p>{@code server.tomcat.max-http-form-post-size} 只作用于表单，JSON 请求体需要在读取前
 * 按 {@code Content-Length} 拦截，防止超大 payload 占满内存。仅作用于
 * {@code POST /alert/v1/internal/prometheus}，不影响其它接口。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class AlertIngestSizeFilter extends OncePerRequestFilter {

    /** 受保护的入站路径 */
    private static final String INGEST_PATH = "/alert/v1/internal/prometheus";

    private final AlertIngestProperties ingestProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long contentLength = request.getContentLengthLong();
        long max = ingestProperties.getMaxPayloadBytes();
        if (HttpMethod.POST.matches(request.getMethod())
                && INGEST_PATH.equals(request.getRequestURI())
                && contentLength > max) {
            response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":413,\"message\":\"request payload too large\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
