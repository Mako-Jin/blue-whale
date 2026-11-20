package com.bluewhale.common.i18n;

import com.bluewhale.common.core.i18n.LocaleSupport;
import com.bluewhale.common.web.WebHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;

/**
 * 请求语言上下文过滤器。
 *
 * <p>从网关注入的 {@code X-Lang}（优先）或 {@code Accept-Language} 解析语言并写入
 * {@link LocaleContextHolder}，使 {@code MessageSource}、Bean Validation 插值等
 * 在业务代码执行前即可拿到正确 Locale；请求结束恢复原值，避免线程池复用串语言。</p>
 *
 * @author BlueWhale
 */
public class LocaleContextFilter extends OncePerRequestFilter {

    /**
     * 解析请求语言并绑定上下文，请求结束后恢复。
     *
     * @param request     HTTP 请求
     * @param response    HTTP 响应
     * @param filterChain 过滤器链
     * @throws ServletException Servlet 异常
     * @throws IOException      IO 异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Locale previous = LocaleContextHolder.getLocale();
        LocaleContextHolder.setLocale(resolveLocale(request));
        try {
            filterChain.doFilter(request, response);
        } finally {
            LocaleContextHolder.setLocale(previous);
        }
    }

    /**
     * 解析请求语言。
     *
     * @param request 当前请求
     * @return 归一化后的语言；缺失或不受支持时返回简体中文
     */
    public static Locale resolveLocale(HttpServletRequest request) {
        String language = LocaleSupport.resolve(
                WebHeaders.lang(request),
                WebHeaders.header(request, WebHeaders.ACCEPT_LANGUAGE));
        return Locale.forLanguageTag(language);
    }
}
