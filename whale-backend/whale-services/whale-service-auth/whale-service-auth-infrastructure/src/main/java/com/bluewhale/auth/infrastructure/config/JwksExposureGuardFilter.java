package com.bluewhale.auth.infrastructure.config;

import com.bluewhale.auth.infrastructure.jwt.JwtKeyMaterial;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * JWKS 端点暴露守卫：HS256 模式下关闭 {@code /oauth2/jwks}。
 *
 * <p>Spring Authorization Server 示例用 {@code ImmutableSecret} 演示 JWKS，但对称密钥模式下的
 * JWKS 会把签名密钥本身（{@code kty=oct}）发布出去；该端点若对外可达，任何调用者都能拿到密钥
 * 并伪造任意用户/租户的令牌。因此 HS256 模式直接返回 404，RS256 模式才允许发布公钥集。</p>
 *
 * <p>令牌内省使用的 {@code JWKSource} 是进程内对象，不经过本过滤器，不受影响。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwksExposureGuardFilter extends OncePerRequestFilter {

    /** 授权服务器标准 JWKS 路径 */
    private static final String JWKS_PATH = "/oauth2/jwks";

    /** HS256 模式下的拒绝响应体 */
    private static final String NOT_FOUND_BODY = "{\"code\":404,\"message\":\"JWKS 仅在 RS256 模式下提供\"}";

    private final JwtKeyMaterial keyMaterial;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (!keyMaterial.isAsymmetric() && isJwksPath(stripContextPath(request))) {
            log.warn("HS256 模式禁止访问 /oauth2/jwks（对称密钥会被公开），请配置 whale.auth.jwt.private-key 切换 RS256");
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.setContentType("application/json;charset=UTF-8");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(NOT_FOUND_BODY);
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 判断是否为 JWKS 路径（含尾斜杠变体，避免 `/oauth2/jwks/` 绕过守卫）
     *
     * @param path 不含 context-path 的请求路径
     * @return 命中 JWKS 端点返回 {@code true}
     */
    private static boolean isJwksPath(String path) {
        return JWKS_PATH.equals(path) || path.startsWith(JWKS_PATH + "/");
    }

    /**
     * 剥离 context-path，得到与协议端点一致的路径
     *
     * @param request 当前请求
     * @return 不含部署前缀的路径
     */
    private static String stripContextPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath == null || contextPath.isEmpty() || !uri.startsWith(contextPath)) {
            return uri;
        }
        return uri.substring(contextPath.length());
    }
}
