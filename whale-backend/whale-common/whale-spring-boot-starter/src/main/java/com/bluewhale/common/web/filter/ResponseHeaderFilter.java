package com.bluewhale.common.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 公共安全响应头过滤器：为全部 Web 响应补充禁缓存与安全相关响应头。
 *
 * <p>迁移自参考基座 {@code sts-common-web} 的 {@code AddResponseHeaderFilter}，并修正其
 * 未调用 {@link FilterChain#doFilter} 导致请求链中断的问题。</p>
 *
 * @author BlueWhale
 */
public class ResponseHeaderFilter extends OncePerRequestFilter {

    /** 禁缓存响应头 */
    static final String HEADER_CACHE_CONTROL = "Cache-Control";
    /** 禁缓存值（HTTP/1.0 兼容头） */
    static final String HEADER_PRAGMA = "Pragma";
    /** 过期时间头 */
    static final String HEADER_EXPIRES = "Expires";
    /** 内容类型嗅探防护头 */
    static final String HEADER_X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
    /** 禁止内嵌框架头 */
    static final String HEADER_X_FRAME_OPTIONS = "X-Frame-Options";
    /** 浏览器 XSS 过滤器头 */
    static final String HEADER_X_XSS_PROTECTION = "X-XSS-Protection";

    /** 禁缓存取值 */
    static final String CACHE_CONTROL_NO_CACHE = "no-store, no-cache, must-revalidate";
    /** 禁用缓存取值 */
    static final String PRAGMA_NO_CACHE = "no-cache";
    /** 立即过期取值 */
    static final String EXPIRES_IMMEDIATE = "0";
    /** 禁止 MIME 嗅探取值 */
    static final String NOSNIFF = "nosniff";
    /** 禁止被内嵌取值 */
    static final String FRAME_DENY = "DENY";
    /** 启用浏览器 XSS 过滤取值 */
    static final String XSS_PROTECTION_ENABLED = "1; mode=block";

    /**
     * 写入安全响应头后继续过滤器链
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
        response.setHeader(HEADER_CACHE_CONTROL, CACHE_CONTROL_NO_CACHE);
        response.setHeader(HEADER_PRAGMA, PRAGMA_NO_CACHE);
        response.setHeader(HEADER_EXPIRES, EXPIRES_IMMEDIATE);
        response.setHeader(HEADER_X_CONTENT_TYPE_OPTIONS, NOSNIFF);
        response.setHeader(HEADER_X_FRAME_OPTIONS, FRAME_DENY);
        response.setHeader(HEADER_X_XSS_PROTECTION, XSS_PROTECTION_ENABLED);
        filterChain.doFilter(request, response);
    }
}
