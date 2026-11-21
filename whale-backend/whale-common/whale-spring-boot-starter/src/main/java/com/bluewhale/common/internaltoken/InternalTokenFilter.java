package com.bluewhale.common.internaltoken;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * 服务侧内网接口令牌过滤器（V-02 纵深防御）。
 *
 * <p>对命中 {@code pathPatterns} 且未命中 {@code excludePatterns} 的请求校验令牌：
 * 请求头令牌与服务端配置一致才放行，否则短路返回 403 JSON（与业务侧 {@code Result} 同结构）。
 * 令牌比较使用 {@link MessageDigest#isEqual} 常量时间比较，避免时序侧信道。</p>
 *
 * <p><b>两种校验模式：</b>配置 {@code bluewhale.internal-token.issuers} 后进入「按服务签发」模式，
 * 请求头值必须为 {@code <服务ID>.<密钥>}；非平台服务还要求 {@code X-Tenant-Id} 存在于该服务的
 * 租户白名单，防止持有一个服务令牌的调用方伪造其他租户。未配置 issuers 时沿用共享令牌。</p>
 *
 * <p>注册顺序为最高优先级（早于 Spring Security 过滤链），保证内网路径未携带合法令牌时
 * 直接 403，而非回落为鉴权 401；未启用或令牌未配置时不拦截（dev 环境由
 * {@link InternalTokenGuard} 告警，非 dev 由其拒绝启动）。</p>
 *
 * @author BlueWhale
 */
@Slf4j
public class InternalTokenFilter extends OncePerRequestFilter {

    /** 令牌缺失或不正确时的业务状态码 */
    private static final int DENY_CODE = 403;

    /** 令牌缺失或不正确时的提示文案 */
    private static final String DENY_MESSAGE = "内网接口令牌缺失或不正确";

    /** 非平台服务缺少租户头时的提示文案 */
    private static final String TENANT_MISSING_MESSAGE = "内网接口缺少租户头";

    /** 服务令牌越权访问租户时的提示文案 */
    private static final String TENANT_DENIED_MESSAGE = "内网令牌无权访问该租户";

    /** JSON 响应内容类型 */
    private static final String JSON_CONTENT_TYPE = "application/json;charset=UTF-8";

    private final InternalTokenProperties properties;

    /** Ant 路径匹配器（线程安全，复用单例） */
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * 构造内网令牌过滤器
     *
     * @param properties 内网令牌配置
     */
    public InternalTokenFilter(InternalTokenProperties properties) {
        this.properties = properties;
    }

    /**
     * 校验内网请求令牌：命中受保护路径且未命中豁免路径时要求合法令牌，否则放行
     *
     * @param request     当前请求
     * @param response    当前响应
     * @param filterChain 过滤器链
     * @throws ServletException      过滤器链异常
     * @throws IOException           响应写出异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }
        String sharedToken = properties.resolveToken();
        if (!properties.hasIssuers() && sharedToken.isBlank()) {
            // 未配置任何令牌：fail-open（dev 已由 Guard 告警，非 dev 启动即被 Guard 拒绝）
            filterChain.doFilter(request, response);
            return;
        }
        String path = request.getRequestURI();
        if (!isProtected(path) || isExcluded(path)) {
            filterChain.doFilter(request, response);
            return;
        }
        String provided = request.getHeader(properties.getHeader());
        if (provided == null) {
            log.warn("内网接口缺少令牌头: path={}, remote={}", path, request.getRemoteAddr());
            writeDenied(response, DENY_MESSAGE);
            return;
        }
        if (properties.hasIssuers()) {
            if (!verifyIssuer(request, response, path, provided)) {
                return;
            }
        } else if (!constantTimeEquals(sharedToken, provided)) {
            log.warn("内网接口令牌校验失败: path={}, remote={}", path, request.getRemoteAddr());
            writeDenied(response, DENY_MESSAGE);
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 按服务签发模式校验：解析 {@code <服务ID>.<密钥>}，校验密钥与非平台服务的租户白名单
     *
     * @param request  当前请求
     * @param response 当前响应
     * @param path     请求路径（日志用）
     * @param provided 请求头令牌
     * @return 校验通过返回 {@code true}；失败已写出 403 并返回 {@code false}
     * @throws IOException 响应写出异常
     */
    private boolean verifyIssuer(HttpServletRequest request, HttpServletResponse response,
                                 String path, String provided) throws IOException {
        int dot = provided.indexOf('.');
        if (dot <= 0 || dot == provided.length() - 1) {
            return denyIssuer(response, path, DENY_MESSAGE);
        }
        String issuerId = provided.substring(0, dot);
        String secret = provided.substring(dot + 1);
        InternalTokenProperties.Issuer issuer = properties.getIssuers().get(issuerId);
        if (issuer == null || issuer.getSecret() == null
                || !constantTimeEquals(issuer.getSecret(), secret)) {
            return denyIssuer(response, path, DENY_MESSAGE);
        }
        if (issuer.isPlatform()) {
            return true;
        }
        String tenantId = request.getHeader(properties.getTenantHeader());
        if (tenantId == null || tenantId.isBlank()) {
            return denyIssuer(response, path, TENANT_MISSING_MESSAGE);
        }
        if (!tenantAllowed(issuer, tenantId.trim())) {
            return denyIssuer(response, path, TENANT_DENIED_MESSAGE);
        }
        return true;
    }

    /**
     * 租户是否在服务允许范围内（空白项忽略，精确匹配）
     *
     * @param issuer   服务令牌配置
     * @param tenantId 请求租户
     * @return 允许返回 {@code true}
     */
    private boolean tenantAllowed(InternalTokenProperties.Issuer issuer, String tenantId) {
        if (issuer.getTenants() == null) {
            return false;
        }
        for (String allowed : issuer.getTenants()) {
            if (allowed != null && allowed.trim().equals(tenantId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 记录越权日志并写出 403
     *
     * @param response 当前响应
     * @param path     请求路径
     * @param message  拒绝原因
     * @return 恒为 {@code false}，便于调用方直接 return
     * @throws IOException 响应写出异常
     */
    private boolean denyIssuer(HttpServletResponse response, String path, String message)
            throws IOException {
        log.warn("内网接口拒绝访问: path={}, reason={}", path, message);
        writeDenied(response, message);
        return false;
    }

    /**
     * 判断请求路径是否命中受保护模式
     *
     * @param path 请求路径
     * @return 命中任一受保护模式返回 {@code true}
     */
    private boolean isProtected(String path) {
        return matchesAny(properties.getPathPatterns(), path);
    }

    /**
     * 判断请求路径是否命中豁免模式
     *
     * @param path 请求路径
     * @return 命中任一豁免模式返回 {@code true}
     */
    private boolean isExcluded(String path) {
        return matchesAny(properties.getExcludePatterns(), path);
    }

    /**
     * 路径是否命中任一 Ant 模式
     *
     * @param patterns 模式列表
     * @param path     请求路径
     * @return 命中返回 {@code true}
     */
    private boolean matchesAny(List<String> patterns, String path) {
        if (patterns == null || patterns.isEmpty()) {
            return false;
        }
        for (String pattern : patterns) {
            if (pattern != null && pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 常量时间令牌比较，避免时序侧信道
     *
     * @param expected 期望令牌
     * @param actual   请求携带令牌
     * @return 相等返回 {@code true}
     */
    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 写出 403 令牌校验失败响应（与业务侧 Result 同结构 JSON 体）
     *
     * @param response 当前响应
     * @param message  拒绝原因
     * @throws IOException 响应写出异常
     */
    private void writeDenied(HttpServletResponse response, String message) throws IOException {
        response.setStatus(DENY_CODE);
        response.setContentType(JSON_CONTENT_TYPE);
        response.getWriter().write("{\"code\":" + DENY_CODE
                + ",\"message\":\"" + message + "\",\"data\":null}");
    }
}
