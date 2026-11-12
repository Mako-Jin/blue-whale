package com.bluewhale.gateway.filter;

import com.bluewhale.gateway.common.GatewayResponse;
import com.bluewhale.gateway.config.WhaleGatewayProperties;
import com.bluewhale.gateway.security.MembershipRevocationChecker;
import com.bluewhale.gateway.security.TenantStatusChecker;
import com.bluewhale.gateway.security.TokenBlacklistChecker;
import com.bluewhale.gateway.security.UserAccessRevocationChecker;
import com.bluewhale.security.token.TokenTimeSupport;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
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
import java.util.Date;

/**
 * JWT 全局认证过滤器。
 * 约定与 whale-service-auth 签发侧一字不差：HS256；claims：sub=username、userId、roles（逗号分隔）、tokenType。
 * 认证通过后向下游注入 X-User-Id、X-Username、X-User-Roles（先清除外部伪造的同名入站头）。
 * <p>校验顺序：签名/过期 → 令牌类型必须为 access（拒绝以 refreshToken 冒充访问令牌）→
 * 查询分布式登出黑名单（REQ-SEC-003）→ 租户停用校验 → 租户成员撤销校验 →
 * 用户级撤销校验 → 强制改密拦截（REQ-SEC-023）→ 注入身份头放行。
 * 强制改密拦截置于全部撤销校验之后：改密后旧令牌已撤销，应返回 401
 * （未认证）而非 403（强制改密），避免客户端误弹改密对话框。</p>
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
    /** 强制改密标记 claim（REQ-SEC-023，与 whale-service-auth 签发侧逐字一致） */
    private static final String CLAIM_PWD_CHANGE_REQUIRED = "pwdChangeRequired";
    /** 强制改密状态下仍可访问的路径（自助改密 / 登出 / 刷新 / 当前用户） */
    private static final String[] PASSWORD_CHANGE_ALLOWED_PATTERNS = {
            "/auth/v1/password", "/auth/v1/logout", "/auth/v1/refresh", "/auth/v1/me"};

    /** 认证拒绝计数指标名 */
    private static final String METER_REJECTED = "gateway.auth.rejected";

    /** 指标拒绝原因标签名 */
    private static final String TAG_REASON = "reason";

    /** 拒绝原因：缺少 Bearer 令牌 */
    private static final String REASON_MISSING_TOKEN = "missing_token";

    /** 拒绝原因：签名/过期等验签失败 */
    private static final String REASON_INVALID_TOKEN = "invalid_token";

    /** 拒绝原因：非 accessToken */
    private static final String REASON_WRONG_TOKEN_TYPE = "wrong_token_type";

    /** 拒绝原因：令牌缺少租户或用户标识 */
    private static final String REASON_MISSING_CLAIM = "missing_claim";

    /** 拒绝原因：令牌命中登出黑名单 */
    private static final String REASON_BLACKLISTED = "blacklisted";

    /** 拒绝原因：租户停用或到期 */
    private static final String REASON_TENANT_UNAVAILABLE = "tenant_unavailable";

    /** 拒绝原因：租户成员关系已解除 */
    private static final String REASON_MEMBERSHIP_REVOKED = "membership_revoked";

    /** 拒绝原因：用户停用/删除/改密 */
    private static final String REASON_USER_REVOKED = "user_revoked";

    /** 拒绝原因：强制改密未完成 */
    private static final String REASON_PASSWORD_CHANGE_REQUIRED = "password_change_required";

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final WhaleGatewayProperties properties;
    private final TokenBlacklistChecker tokenBlacklistChecker;

    /** 指标注册表提供者：容器无 MeterRegistry 时返回 {@code null}，指标静默跳过 */
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    private final TenantStatusChecker tenantStatusChecker;

    private final MembershipRevocationChecker membershipRevocationChecker;

    private final UserAccessRevocationChecker userAccessRevocationChecker;

    /**
     * 构造过滤器，注入网关配置、令牌黑名单、租户状态与撤销校验器
     *
     * @param properties                  网关配置属性
     * @param tokenBlacklistChecker       令牌黑名单校验器
     * @param tenantStatusChecker         租户状态校验器（停用租户存量令牌拒绝）
     * @param membershipRevocationChecker 租户成员撤销校验器（解绑后存量令牌拒绝）
     * @param userAccessRevocationChecker 用户级撤销校验器（停用/删除/改密后存量令牌拒绝）
     * @param meterRegistryProvider       Micrometer 指标注册表提供者，缺省时返回 {@code null} 表示不采集指标
     */
    public JwtAuthGlobalFilter(WhaleGatewayProperties properties, TokenBlacklistChecker tokenBlacklistChecker,
                               TenantStatusChecker tenantStatusChecker,
                               MembershipRevocationChecker membershipRevocationChecker,
                               UserAccessRevocationChecker userAccessRevocationChecker,
                               ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.properties = properties;
        this.tokenBlacklistChecker = tokenBlacklistChecker;
        this.tenantStatusChecker = tenantStatusChecker;
        this.membershipRevocationChecker = membershipRevocationChecker;
        this.userAccessRevocationChecker = userAccessRevocationChecker;
        this.meterRegistryProvider = meterRegistryProvider;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (isWhiteListed(path)) {
            // 白名单不携带可信身份：清除客户端伪造的身份/租户头，避免审计归属与下游租户上下文被污染
            return chain.filter(exchange.mutate().request(withoutIdentityHeaders(request)).build());
        }

        String authorization = request.getHeaders().getFirst(AUTHORIZATION_HEADER);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return unauthorized(exchange, REASON_MISSING_TOKEN);
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

            // 仅 accessToken 可访问下游 API，refreshToken 只能用于 /auth/v1/refresh（白名单）
            if (!TOKEN_TYPE_ACCESS.equals(stringify(claims.get(CLAIM_TOKEN_TYPE)))) {
                log.warn("非 accessToken 访问受保护资源: path={}", path);
                return unauthorized(exchange, REASON_WRONG_TOKEN_TYPE);
            }
            // 令牌必须绑定租户：旧令牌（无 tenantId claim）一律拒绝，避免下游静默回落默认租户
            String tenantId = stringify(claims.get(CLAIM_TENANT_ID));
            if (tenantId.isBlank()) {
                log.warn("accessToken 缺少 tenantId claim: path={}", path);
                return unauthorized(exchange, REASON_MISSING_CLAIM);
            }
            // 令牌必须绑定用户：缺失 userId 无法校验用户级撤销，也无法向下游注入可靠身份
            String userId = stringify(claims.get(CLAIM_USER_ID));
            if (userId.isBlank()) {
                log.warn("accessToken 缺少 userId claim: path={}", path);
                return unauthorized(exchange, REASON_MISSING_CLAIM);
            }
            return tokenBlacklistChecker.isBlacklisted(token)
                    .flatMap(blacklisted -> {
                        if (Boolean.TRUE.equals(blacklisted)) {
                            log.warn("令牌命中登出黑名单: path={}", path);
                            return unauthorized(exchange, REASON_BLACKLISTED);
                        }
                        return tenantStatusChecker.isUnavailable(tenantId)
                                .flatMap(unavailable -> {
                                    if (Boolean.TRUE.equals(unavailable)) {
                                        log.warn("租户已停用或已到期，拒绝存量令牌访问: path={}, tenantId={}",
                                                path, tenantId);
                                        return unauthorized(exchange, REASON_TENANT_UNAVAILABLE);
                                    }
                                    return verifyRevocationsAndForward(
                                            exchange, request, claims, tenantId, userId, path, chain);
                                });
                    });
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT 校验失败: path={}, reason={}", path, e.getMessage());
            return unauthorized(exchange, REASON_INVALID_TOKEN);
        }
    }

    /**
     * 校验租户成员撤销与用户级撤销，再执行强制改密拦截，通过后注入身份头并转发下游。
     *
     * @param exchange 当前交换对象
     * @param request 原始请求
     * @param claims 已验签的 JWT 载荷
     * @param tenantId 令牌租户 ID
     * @param userId 令牌用户 ID
     * @param path 请求路径（仅用于日志）
     * @param chain 下游过滤器链
     * @return 完成信号
     */
    private Mono<Void> verifyRevocationsAndForward(ServerWebExchange exchange, ServerHttpRequest request,
                                                   Claims claims, String tenantId, String userId,
                                                   String path, GatewayFilterChain chain) {
        Date issuedAt = new Date(TokenTimeSupport.issuedAtMillis(claims));
        return membershipRevocationChecker.isRevoked(tenantId, userId, issuedAt)
                .flatMap(membershipRevoked -> {
                    if (Boolean.TRUE.equals(membershipRevoked)) {
                        log.warn("租户成员关系已解除，拒绝存量令牌访问: path={}, tenantId={}, userId={}",
                                path, tenantId, userId);
                        return unauthorized(exchange, REASON_MEMBERSHIP_REVOKED);
                    }
                    return userAccessRevocationChecker.isRevoked(userId, issuedAt)
                            .flatMap(userRevoked -> {
                                if (Boolean.TRUE.equals(userRevoked)) {
                                    log.warn("用户已停用/删除/改密，拒绝存量令牌访问: path={}, userId={}",
                                            path, userId);
                                    return unauthorized(exchange, REASON_USER_REVOKED);
                                }
                                // REQ-SEC-023：强制改密/口令到期拦截（auth 服务侧还有第二层拦截）。
                                // 置于撤销校验之后：已改密/停用的旧令牌（撤销生效）优先 401，
                                // 仅对「身份有效且未撤销」的强制改密令牌返回 403
                                if (Boolean.parseBoolean(stringify(claims.get(CLAIM_PWD_CHANGE_REQUIRED)))
                                        && !isPasswordChangeAllowed(path)) {
                                    log.warn("令牌携带强制改密标记，拒绝访问业务接口: path={}, userId={}",
                                            path, userId);
                                    return forbidden(exchange, REASON_PASSWORD_CHANGE_REQUIRED);
                                }
                                ServerHttpRequest mutatedRequest =
                                        withIdentityHeaders(request, claims, tenantId);
                                return chain.filter(exchange.mutate().request(mutatedRequest).build());
                            });
                });
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
     * 判断请求路径在强制改密状态下是否仍放行
     *
     * @param path 请求路径
     * @return 命中改密白名单返回 {@code true}
     */
    private boolean isPasswordChangeAllowed(String path) {
        for (String pattern : PASSWORD_CHANGE_ALLOWED_PATTERNS) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
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
     * 清除入站身份与租户头（仅用于免认证白名单路径）
     *
     * <p>白名单路径（登录、刷新、审计埋点采集、签名回调、IM 握手等）不经过 JWT 验签，
     * 客户端可自带任意 {@code X-User-Id}/{@code X-Username}/{@code X-User-Roles}/{@code X-Tenant-Id}。
     * 若不清除，下游按网关注入头获取当前用户的接口（如审计埋点归属）会被伪造身份污染。</p>
     *
     * <p><b>保留 {@code Authorization} 是有意为之</b>：{@code /auth/v1/refresh} 等白名单接口
     * 需要读取客户端携带的 refreshToken（或 Bearer 头）完成续期/登出，网关剥离会直接打断该流程；
     * 下游 auth 服务对白名单接口自行决定是否解析该头，不依赖网关做二次裁剪。</p>
     *
     * @param request 原始请求
     * @return 清除身份/租户头后的新请求
     */
    private static ServerHttpRequest withoutIdentityHeaders(ServerHttpRequest request) {
        return request.mutate()
                .headers(headers -> {
                    headers.remove(USER_ID_HEADER);
                    headers.remove(USERNAME_HEADER);
                    headers.remove(USER_ROLES_HEADER);
                    headers.remove(TENANT_ID_HEADER);
                })
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
     * @param reason   拒绝原因（用于指标标签）
     * @return 写出响应完成的信号
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange, String reason) {
        countRejected(reason);
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

    /**
     * 返回 403 强制改密 JSON 响应（REQ-SEC-023），前端据此弹出改密对话框
     *
     * @param exchange 当前请求上下文
     * @param reason   拒绝原因（用于指标标签）
     * @return 写出响应完成的信号
     */
    private Mono<Void> forbidden(ServerWebExchange exchange, String reason) {
        countRejected(reason);
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.parseMediaType("application/json;charset=UTF-8"));
        String traceId = exchange.getRequest().getHeaders().getFirst(TraceIdGlobalFilter.TRACE_ID_HEADER);
        if (traceId != null && !traceId.isBlank()) {
            response.getHeaders().set(TraceIdGlobalFilter.TRACE_ID_HEADER, traceId);
        }
        String body = GatewayResponse.of(403, "首次登录或口令已到期，请先修改口令").toJson();
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * 记录一次认证拒绝计数（容器无 {@link MeterRegistry} 时静默跳过）
     *
     * @param reason 拒绝原因
     */
    private void countRejected(String reason) {
        MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.counter(METER_REJECTED, TAG_REASON, reason).increment();
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
