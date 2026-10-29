package com.bluewhale.common.context;

import com.bluewhale.common.web.WebHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 请求上下文过滤器：从网关可信注入的请求头解析调用者身份与链路信息并绑定到当前线程。
 *
 * <p>解析来源：{@code X-User-Id} / {@code X-Username} / {@code X-Tenant-Id} / {@code X-Trace-Id}。
 * 请求结束无论成败都清理 {@link RequestContextHolder}，避免线程池复用导致身份串号。
 * 租户行级隔离仍由 {@code TenantContextFilter} 写入 {@code TenantContext}，两者互不替代。</p>
 *
 * @author BlueWhale
 */
@Slf4j
public class RequestContextFilter extends OncePerRequestFilter {

    /**
     * 解析请求头并绑定上下文，请求结束后清理
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
        RequestContextHolder.set(new RequestContext(
                parseUserId(request),
                WebHeaders.username(request),
                parseTenantId(request),
                WebHeaders.traceId(request)));
        try {
            filterChain.doFilter(request, response);
        } finally {
            RequestContextHolder.clear();
        }
    }

    /**
     * 解析用户 ID：非数字视为未提供
     *
     * @param request 当前请求
     * @return 用户 ID；缺失或非法时返回 {@code null}
     */
    private static Long parseUserId(HttpServletRequest request) {
        String raw = WebHeaders.userId(request);
        if (raw == null) {
            return null;
        }
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException e) {
            log.debug("网关注入的 X-User-Id 非法，按未提供处理: value={}", raw);
            return null;
        }
    }

    /**
     * 解析租户 ID：非数字视为未提供
     *
     * @param request 当前请求
     * @return 租户 ID；缺失或非法时返回 {@code null}
     */
    private static Long parseTenantId(HttpServletRequest request) {
        String raw = WebHeaders.tenantId(request);
        if (raw == null) {
            return null;
        }
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException e) {
            log.debug("网关注入的 X-Tenant-Id 非法，按未提供处理: value={}", raw);
            return null;
        }
    }
}
