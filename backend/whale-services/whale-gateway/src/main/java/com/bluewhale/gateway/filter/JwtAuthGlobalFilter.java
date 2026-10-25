package com.bluewhale.gateway.filter;

import com.bluewhale.gateway.common.GatewayResponse;
import com.bluewhale.gateway.config.WhaleGatewayProperties;
import com.bluewhale.gateway.security.TenantStatusChecker;
import com.bluewhale.gateway.security.TokenBlacklistChecker;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * JWT 全局认证过滤器。
 * 约定与 whale-auth 签发侧一字不差：HS256；claims：sub=username、userId、roles（逗号分隔）、tokenType。
 * 认证通过后向下游注入 X-User-Id、X-Username、X-User-Roles（先清除外部伪造的同名入站头）。
 * <p>校验顺序：签名/过期 → 令牌类型必须为 access（拒绝以 refreshToken 冒充访问令牌）→
 * 查询分布式登出黑名单（REQ-SEC-003）→ 注入身份头放行。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USERNAME_HEADER = "X-Username";
    private static final String USER_ROLES_HEADER = "X-User-Roles";
    private static final String TENANT_ID_HEADER = "X-Tenant-Id";
    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_TENANT_ID = "tenantId";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_TOKEN_TYPE = "tokenType";
    private static final String TOKEN_TYPE_ACCESS = "access";

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final WhaleGatewayProperties properties;
    private final TokenBlacklistChecker tokenBlacklistChecker;

    private final TenantStatusChecker tenantStatusChecker;

    /**
     * 构造过滤器，注入网关配置与令牌黑名单校验器
     *
     * @param properties            网关配置属性
     * @param tokenBlacklistChecker 令牌黑名单校验器
     * @param tenantStatusChecker   租户状态校验器（停用租户存量令牌拒绝）
     */
    public JwtAuthGlobalFilter(WhaleGatewayProperties properties, TokenBlacklistChecker tokenBlacklistChecker,
                               TenantStatusChecker tenantStatusChecker) {
        this.properties = properties;
        this.tokenBlacklistChecker = tokenBlacklistChecker;
        this.tenantStatusChecker = tenantStatusChecker;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (isWhiteListed(path)) {
            // 白名单同样清除外部伪造的租户头，避免绕过认证后污染下游租户上下文
            return chain.filter(exchange.mutate().request(withoutTenantHeader(request)).build());
        }

        String authorization = request.getHeaders().getFirst(AUTHORIZATION_HEADER);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return unauthorized(exchange);
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        try {
            SecretKey key = Keys.hmacShaKeyFor(
                    properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            // 仅 accessToken 可访问下游 API，refreshToken 只能用于 /auth/refresh（白名单）
            if (!TOKEN_TYPE_ACCESS.equals(stringify(claims.get(CLAIM_TOKEN_TYPE)))) {
                log.warn("非 accessToken 访问受保护资源: path={}", path);
                return unauthorized(exchange);
            }
            // 令牌必须绑定租户：旧令牌（无 tenantId claim）一律拒绝，避免下游静默回落默认租户
            String tenantId = stringify(claims.get(CLAIM_TENANT_ID));
            if (tenantId.isBlank()) {
                log.warn("accessToken 缺少 tenantId claim: path={}", path);
                return unauthorized(exchange);
            }
            return tokenBlacklistChecker.isBlacklisted(token)
                    .flatMap(blacklisted -> {
                        if (Boolean.TRUE.equals(blacklisted)) {
                            log.warn("令牌命中登出黑名单: path={}", path);
                            return unauthorized(exchange);
                        }
                        return tenantStatusChecker.isDisabled(tenantId)
                                .flatMap(disabled -> {
                                    if (Boolean.TRUE.equals(disabled)) {
                                        log.warn("租户已停用，拒绝存量令牌访问: path={}, tenantId={}", path, tenantId);
                                        return unauthorized(exchange);
                                    }
                                    ServerHttpRequest mutatedRequest =
                                            withIdentityHeaders(request, claims, tenantId);
                                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                                });
                    });
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT 校验失败: path={}, reason={}", path, e.getMessage());
            return unauthorized(exchange);
        }
    }

    /**
     * 判断请求路径是否命中免认证白名单
     *
     * @param path 请求路径
     * @return 命中白名单返回 {@code true}
     */
    private boolean isWhiteListed(String path) {
        return properties.getWhiteList().stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    /**
     * 先移除外部伪造的身份与租户头，再注入网关解析出的身份信息
     *
     * @param request  原始请求
     * @param claims   已通过签名校验的令牌载荷
     * @param tenantId 令牌中的租户 ID
     * @return 注入身份头后的新请求
     */
    private static ServerHttpRequest withIdentityHeaders(ServerHttpRequest request, Claims claims, String tenantId) {
        String userId = stringify(claims.get(CLAIM_USER_ID));
        String username = stringify(claims.getSubject());
        String roles = stringify(claims.get(CLAIM_ROLES));
        return request.mutate()
                .headers(headers -> {
                    headers.remove(USER_ID_HEADER);
                    headers.remove(USERNAME_HEADER);
                    headers.remove(USER_ROLES_HEADER);
                    headers.remove(TENANT_ID_HEADER);
                    headers.set(USER_ID_HEADER, userId);
                    headers.set(USERNAME_HEADER, username);
                    headers.set(USER_ROLES_HEADER, roles);
                    headers.set(TENANT_ID_HEADER, tenantId);
                })
                .build();
    }

    /**
     * 清除入站租户头（仅用于免认证白名单路径）
     *
     * @param request 原始请求
     * @return 清除 {@code X-Tenant-Id} 后的新请求
     */
    private static ServerHttpRequest withoutTenantHeader(ServerHttpRequest request) {
        return request.mutate()
                .headers(headers -> headers.remove(TENANT_ID_HEADER))
                .build();
    }

    /**
     * 将令牌载荷值安全转为字符串，null 转为空串
     *
     * @param value 待转换的载荷值
     * @return 字符串形式的值
     */
    private static String stringify(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * 直接返回 401 未认证 JSON 响应，并透传链路追踪头
     *
     * @param exchange 当前请求上下文
     * @return 写出响应完成的信号
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.parseMediaType("application/json;charset=UTF-8"));
        String traceId = exchange.getRequest().getHeaders().getFirst(TraceIdGlobalFilter.TRACE_ID_HEADER);
        if (traceId != null && !traceId.isBlank()) {
            response.getHeaders().set(TraceIdGlobalFilter.TRACE_ID_HEADER, traceId);
        }
        String body = GatewayResponse.of(401, "未认证").toJson();
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
