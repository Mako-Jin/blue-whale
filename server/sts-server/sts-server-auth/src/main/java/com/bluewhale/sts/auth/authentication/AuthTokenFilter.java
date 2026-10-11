package com.bluewhale.sts.auth.authentication;

import com.bluewhale.sts.auth.common.codec.JwtCodec;
import com.bluewhale.sts.auth.common.codec.JwtPayload;
import com.bluewhale.sts.auth.common.codec.JwtVerificationException;
import com.bluewhale.sts.auth.context.AuthContext;
import com.bluewhale.sts.auth.context.AuthContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 认证过滤器 —— 把请求头里的 Bearer 令牌翻译成 {@link AuthContext}。
 *
 * <p><b>它只负责"翻译"，不负责"拒绝"。</b>
 * 没有令牌、令牌坏了、令牌过期了 —— 这些都只是"这次请求是匿名的"，
 * 由 {@code AuthInterceptor} 在需要权限的地方按需拒绝。
 * 让过滤器一律拒绝的写法，会把登录接口、健康检查、静态资源也一起挡掉，
 * 然后不得不再加一张白名单 —— 白名单一旦漏一个，那个接口就永久 401。
 *
 * <p><b>无状态：不查库。</b>令牌自带 userId / tenantId，过滤器只做密码学校验。
 * 权限（角色/资源）留到真正判定时再按需装载 —— 这样每次请求的开销
 * 与"这个接口是否需要鉴权"成正比，而不是固定地查一遍库。
 *
 * <p><b>finally 里必须清理 ThreadLocal。</b>Tomcat 线程是复用的，
 * 不清理会把上一个请求的身份带进下一个请求，症状是"偶发串号"，
 * 是 ThreadLocal 最危险也最难查的失效方式。
 */
public class AuthTokenFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthTokenFilter.class);

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtCodec jwtCodec;

    public AuthTokenFilter(JwtCodec jwtCodec) {
        this.jwtCodec = jwtCodec;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = resolveToken(request);
            if (token != null) {
                populateContext(token);
            }
            filterChain.doFilter(request, response);
        } finally {
            AuthContextHolder.clear();
        }
    }

    private void populateContext(String token) {
        try {
            JwtPayload payload = jwtCodec.verify(token, System.currentTimeMillis());
            // 刷新令牌不能当访问令牌用：它有效期长得多，混用等于把长寿命凭证当短寿命用
            if (!payload.isAccessToken()) {
                log.debug("请求携带的不是访问令牌，按未认证处理 | type={}", payload.tokenType());
                return;
            }
            AuthContext context = new AuthContext();
            context.setUserId(payload.userId());
            context.setTenantId(payload.tenantId());
            context.setUsername(payload.username());
            context.setSessionId(payload.jti());
            context.setTokenExpiresAt(payload.expiresAt());
            AuthContextHolder.set(context);
        } catch (JwtVerificationException ex) {
            // 记 DEBUG 而非 WARN：公网上的探测请求会带各种垃圾令牌，
            // 记 WARN 会让日志被噪声淹没。真正需要关注的失败由登录接口记录。
            log.debug("令牌校验未通过，按未认证处理 | message={}", ex.getMessage());
        }
    }

    private static String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIX)) {
            return null;
        }
        String token = header.substring(PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

}
