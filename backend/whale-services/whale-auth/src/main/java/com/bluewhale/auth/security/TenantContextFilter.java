package com.bluewhale.auth.security;

import com.bluewhale.common.web.WebHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 租户上下文过滤器（Wave 5.54 Phase 1）。
 *
 * <p>从网关可信注入的 {@code X-Tenant-Id} 请求头解析租户 ID 并写入 {@link TenantContext}，
 * 请求结束后清理 ThreadLocal。登录/刷新等白名单请求无租户头，上下文保持为空，
 * 由登录链路使用 {@link LoginTenantContext} 显式指定租户，二者互不干扰。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TenantContextFilter extends OncePerRequestFilter {

    /**
     * 解析请求头中的租户 ID，绑定到当前线程；无论成功与否，请求结束必须清理
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
        String raw = WebHeaders.tenantId(request);
        if (StringUtils.hasText(raw)) {
            try {
                TenantContext.set(Long.parseLong(raw.trim()));
            } catch (NumberFormatException e) {
                log.warn("租户请求头非法，忽略: path={}, X-Tenant-Id={}", request.getRequestURI(), raw);
            }
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
