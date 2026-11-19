package com.bluewhale.gateway.filter;

import com.bluewhale.gateway.accesslist.GatewayAccessListProvider;
import com.bluewhale.gateway.common.GatewayHeaders;
import com.bluewhale.gateway.common.GatewayMessageResolver;
import com.bluewhale.gateway.common.GatewayResponseWriter;
import com.bluewhale.gateway.config.WhaleGatewayProperties;
import com.bluewhale.gateway.security.JwksKeyStore;
import com.bluewhale.gateway.security.MembershipRevocationChecker;
import com.bluewhale.gateway.security.TenantStatusChecker;
import com.bluewhale.gateway.security.TokenBlacklistChecker;
import com.bluewhale.gateway.security.TokenHeader;
import com.bluewhale.gateway.security.UserAccessRevocationChecker;
import com.bluewhale.security.contract.GatewayContract;
import com.bluewhale.security.token.TokenTimeSupport;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParserBuilder;
import io.jsonwebtoken.Jwts;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple4;

import java.security.Key;
import java.security.PublicKey;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

/**
 * JWT 全局认证过滤器。
 * 约定与 whale-service-auth 签发侧一致：配置 RSA 私钥时 RS256（头带 {@code kid}），回退 HS256；
 * claims：sub=username、userId、tenantId、roles（逗号分隔）、tokenType。
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
    private static final String USER_ID_HEADER = GatewayHeaders.USER_ID;
    private static final String USERNAME_HEADER = GatewayHeaders.USERNAME;
    private static final String USER_ROLES_HEADER = GatewayHeaders.USER_ROLES;
    private static final String TENANT_ID_HEADER = GatewayHeaders.TENANT_ID;
    private static final String CLAIM_USER_ID = GatewayContract.Claims.USER_ID;
    private static final String CLAIM_TENANT_ID = GatewayContract.Claims.TENANT_ID;
    private static final String CLAIM_ROLES = GatewayContract.Claims.ROLES;
    private static final String CLAIM_TOKEN_TYPE = GatewayContract.Claims.TOKEN_TYPE;
    private static final String TOKEN_TYPE_ACCESS = GatewayContract.Claims.TYPE_ACCESS;
    /** 强制改密标记 claim（REQ-SEC-023，与 whale-service-auth 签发侧逐字一致） */
    private static final String CLAIM_PWD_CHANGE_REQUIRED = GatewayContract.Claims.PWD_CHANGE_REQUIRED;
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

    /** 拒绝原因：请求路径命中黑名单 */
    private static final String REASON_PATH_BLACKLISTED = "path_blacklisted";

    /**
     * 允许的签发时间超前量（时钟漂移 5 分钟）。
     *
     * <p>撤销判定是「{@code iat <= 撤销时刻} 即已撤销」，若令牌 {@code iat} 在未来，
     * 该不等式永不成立，用户停用/删除/改密/成员解绑/租户停用对这类令牌全部失效。
     * 因此验签后必须拒绝 {@code iat} 超前当前时间超过该容忍值的令牌。</p>
     */
    private static final long ALLOWED_CLOCK_SKEW_MILLIS = 5L * 60L * 1000L;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final WhaleGatewayProperties properties;
    private final TokenBlacklistChecker tokenBlacklistChecker;

    /** JWKS 公钥仓库（RS256）与 HS256 共享密钥回落的统一入口 */
    private final JwksKeyStore jwksKeyStore;

    /** 指标注册表提供者：容器无 MeterRegistry 时返回 {@code null}，指标静默跳过 */
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    private final TenantStatusChecker tenantStatusChecker;

    private final MembershipRevocationChecker membershipRevocationChecker;

    private final UserAccessRevocationChecker userAccessRevocationChecker;

    /** 远程访问名单提供者（黑名单优先 + 白名单与本地配置兜底） */
    private final GatewayAccessListProvider accessListProvider;

    /**
     * 构造过滤器，注入网关配置、令牌黑名单、租户状态与撤销校验器
     *
     * @param properties                  网关配置属性
     * @param tokenBlacklistChecker       令牌黑名单校验器
     * @param jwksKeyStore                JWKS 公钥仓库（RS256 按 kid 选 key，HS256 回落共享密钥）
     * @param tenantStatusChecker         租户状态校验器（停用租户存量令牌拒绝）
     * @param membershipRevocationChecker 租户成员撤销校验器（解绑后存量令牌拒绝）
     * @param userAccessRevocationChecker 用户级撤销校验器（停用/删除/改密后存量令牌拒绝）
     * @param meterRegistryProvider       Micrometer 指标注册表提供者，缺省时返回 {@code null} 表示不采集指标
     * @param accessListProvider          远程访问名单提供者（黑名单优先，白名单与本地配置取并集）
     */
    public JwtAuthGlobalFilter(WhaleGatewayProperties properties, TokenBlacklistChecker tokenBlacklistChecker,
                               JwksKeyStore jwksKeyStore,
                               TenantStatusChecker tenantStatusChecker,
                               MembershipRevocationChecker membershipRevocationChecker,
                               UserAccessRevocationChecker userAccessRevocationChecker,
                               ObjectProvider<MeterRegistry> meterRegistryProvider,
                               GatewayAccessListProvider accessListProvider) {
        this.properties = properties;
        this.tokenBlacklistChecker = tokenBlacklistChecker;
        this.jwksKeyStore = jwksKeyStore;
        this.tenantStatusChecker = tenantStatusChecker;
        this.membershipRevocationChecker = membershipRevocationChecker;
        this.userAccessRevocationChecker = userAccessRevocationChecker;
        this.meterRegistryProvider = meterRegistryProvider;
        this.accessListProvider = accessListProvider;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        String method = request.getMethod() == null ? null : request.getMethod().name();

        // 黑名单优先于白名单：命中封禁路径立即 403，不再校验令牌/白名单
        if (accessListProvider.matchesBlacklist(path, method)) {
            log.warn("请求命中路径黑名单，拒绝访问: path={}, method={}", path, method);
            return pathForbidden(exchange, REASON_PATH_BLACKLISTED);
        }

        if (isWhiteListed(path, method)) {
            // 白名单不携带可信身份：清除客户端伪造的身份/租户头，避免审计归属与下游租户上下文被污染
            return chain.filter(exchange.mutate().request(withoutIdentityHeaders(request)).build());
        }

        String authorization = request.getHeaders().getFirst(AUTHORIZATION_HEADER);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return unauthorized(exchange, REASON_MISSING_TOKEN);
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        TokenHeader tokenHeader;
        try {
            tokenHeader = TokenHeader.parse(token);
        } catch (IllegalArgumentException e) {
            log.warn("JWT 结构非法: path={}, reason={}", path, e.getMessage());
            return unauthorized(exchange, REASON_INVALID_TOKEN);
        }
        // 先按 kid 解析验签密钥（RS256 可能触发一次 JWKS 刷新），再验签并做存量治理校验。
        // 这里只把「密钥解析失败」降级为 401；验签之后的 Redis 异常仍向上抛出，避免把依赖故障误报成未认证。
        Mono<Key> keyMono = jwksKeyStore.resolve(tokenHeader)
                .onErrorResume(e -> {
                    log.warn("JWT 验签密钥解析失败: path={}, reason={}", path, e.getMessage());
                    return Mono.empty();
                });
        // 注意：Mono<Void> 永远不 emit 值，不能在其后接 switchIfEmpty（会重复写出 401 并触发响应头只读）。
        // 这里先把密钥包成 Optional 保证恰好 emit 一次，再分流到「401」或「验签 + 转发」。
        return keyMono.map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(key -> key.isEmpty()
                        ? unauthorized(exchange, REASON_INVALID_TOKEN)
                        : authenticate(exchange, request, token, key.get(), path, method, chain));
    }

    /**
     * 使用已解析的验签密钥完成验签、claim 校验与存量治理校验。
     *
     * @param exchange 当前交换对象
     * @param request  原始请求
     * @param token    JWT 原文
     * @param key      验签密钥（HS256 共享密钥或 JWKS 公钥）
     * @param path     请求路径
     * @param method   请求方法，可空
     * @param chain    下游过滤器链
     * @return 完成信号
     */
    private Mono<Void> authenticate(ServerWebExchange exchange, ServerHttpRequest request, String token, Key key,
                                    String path, String method, GatewayFilterChain chain) {
        try {
            Claims claims = parseClaims(token, key);

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
            // iat 在未来会让「iat <= 撤销时刻」永不成立，撤销校验被整体绕过，必须先行拒绝
            if (isIssuedInFuture(claims)) {
                log.warn("accessToken 签发时间晚于当前时间（超出容忍时钟漂移），拒绝: path={}", path);
                return unauthorized(exchange, REASON_INVALID_TOKEN);
            }
            return verifyAccessAndForward(exchange, request, claims, tenantId, userId, token, path, method, chain);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT 校验失败: path={}, reason={}", path, e.getMessage());
            return unauthorized(exchange, REASON_INVALID_TOKEN);
        }
    }

    /**
     * 按密钥类型验签并返回 claims
     *
     * @param token JWT 原文
     * @param key   验签密钥
     * @return 令牌载荷
     */
    private static Claims parseClaims(String token, Key key) {
        JwtParserBuilder parser = Jwts.parser();
        if (key instanceof SecretKey secretKey) {
            parser.verifyWith(secretKey);
        } else if (key instanceof PublicKey publicKey) {
            parser.verifyWith(publicKey);
        } else {
            throw new IllegalArgumentException("不支持的验签密钥类型: " + key.getAlgorithm());
        }
        return parser.build().parseSignedClaims(token).getPayload();
    }

    /**
     * 并行执行登出黑名单、租户状态、成员撤销、用户撤销四段校验，再按优先级短路判定并转发下游。
     *
     * <p><b>为何并行</b>：四段校验之间没有数据依赖，只有判定优先级依赖。串行执行时每个带 JWT 的
     * 请求要 4 次 Redis RTT；并行后 Lettuce 在单连接上复用管道，实际约 1 次 RTT，整体鉴权链路
     * 从 5 次往返降为 2 次（本方法 + 权限码头缓存读取）。代价是登出黑名单命中时其余 3 次
     * O(1) GET 也会执行，可接受。</p>
     *
     * <p>判定优先级不变：黑名单 &gt; 租户级白名单（跳过后续治理校验）&gt; 租户停用/到期 &gt;
     * 成员解绑 &gt; 用户撤销 &gt; 强制改密。</p>
     *
     * @param exchange 当前交换对象
     * @param request 原始请求
     * @param claims 已验签的 JWT 载荷
     * @param tenantId 令牌租户 ID
     * @param userId 令牌用户 ID
     * @param token accessToken 原文（登出黑名单键的组成部分）
     * @param path 请求路径（日志与改密白名单判定）
     * @param method 请求方法，可空
     * @param chain 下游过滤器链
     * @return 完成信号
     */
    private Mono<Void> verifyAccessAndForward(ServerWebExchange exchange, ServerHttpRequest request,
                                             Claims claims, String tenantId, String userId, String token,
                                             String path, String method, GatewayFilterChain chain) {
        // GW-08 租户级白名单：命中绑定租户的放行规则时，跳过租户状态、成员/用户撤销与强制改密校验
        // （登出黑名单仍在下方按优先级先行判定）。此处先做内存匹配，避免为放行请求额外短路并发查询。
        boolean tenantWhitelisted = accessListProvider.matchesTenantWhitelist(path, method, tenantId);
        Date issuedAt = new Date(TokenTimeSupport.issuedAtMillis(claims));
        // 四段校验无数据依赖，只有判定优先级依赖：Mono.zip 并行把常态请求的 Redis RTT 从 4~5 次压到 1 次，
        // 代价是黑名单命中时也要等最慢的一路（约 1 次 multiGet），即「异常令牌多付 3 次 O(1) GET」。
        // 每个 checker 内部已各自 onErrorResume 到 SecurityDegradationPolicy，zip 不会因单个依赖故障整体失败；
        // 这是有意的取舍，不要为了黑名单短路再改回串行（否则所有正常请求都要多付 3 次 RTT）。
        Mono<Boolean> blacklisted = tokenBlacklistChecker.isBlacklisted(token)
                .defaultIfEmpty(Boolean.FALSE);
        Mono<Boolean> tenantUnavailable = tenantStatusChecker.isUnavailable(tenantId)
                .defaultIfEmpty(Boolean.FALSE);
        Mono<Boolean> membershipRevoked = membershipRevocationChecker.isRevoked(tenantId, userId, issuedAt)
                .defaultIfEmpty(Boolean.FALSE);
        Mono<Boolean> userRevoked = userAccessRevocationChecker.isRevoked(userId, issuedAt)
                .defaultIfEmpty(Boolean.FALSE);
        return Mono.zip(blacklisted, tenantUnavailable, membershipRevoked, userRevoked)
                .flatMap(results -> decideAccess(exchange, request, claims, tenantId, userId, path, chain,
                        tenantWhitelisted, results));
    }

    /**
     * 按优先级消费并行校验结果：命中即写对应响应，全部通过则注入身份头转发。
     *
     * @param exchange 当前交换对象
     * @param request 原始请求
     * @param claims 已验签的 JWT 载荷
     * @param tenantId 令牌租户 ID
     * @param userId 令牌用户 ID
     * @param path 请求路径
     * @param chain 下游过滤器链
     * @param tenantWhitelisted 是否命中租户级白名单
     * @param results 并行校验结果（T1=黑名单, T2=租户不可用, T3=成员撤销, T4=用户撤销）
     * @return 完成信号
     */
    private Mono<Void> decideAccess(ServerWebExchange exchange, ServerHttpRequest request, Claims claims,
                                    String tenantId, String userId, String path, GatewayFilterChain chain,
                                    boolean tenantWhitelisted,
                                    Tuple4<Boolean, Boolean, Boolean, Boolean> results) {
        if (Boolean.TRUE.equals(results.getT1())) {
            log.warn("令牌命中登出黑名单: path={}", path);
            return unauthorized(exchange, REASON_BLACKLISTED);
        }
        if (tenantWhitelisted) {
            log.info("命中租户级白名单，跳过存量治理校验: path={}, tenantId={}", path, tenantId);
            return forward(exchange, request, claims, tenantId, chain);
        }
        if (Boolean.TRUE.equals(results.getT2())) {
            log.warn("租户已停用或已到期，拒绝存量令牌访问: path={}, tenantId={}", path, tenantId);
            return unauthorized(exchange, REASON_TENANT_UNAVAILABLE);
        }
        if (Boolean.TRUE.equals(results.getT3())) {
            log.warn("租户成员关系已解除，拒绝存量令牌访问: path={}, tenantId={}, userId={}",
                    path, tenantId, userId);
            return unauthorized(exchange, REASON_MEMBERSHIP_REVOKED);
        }
        if (Boolean.TRUE.equals(results.getT4())) {
            log.warn("用户已停用/删除/改密，拒绝存量令牌访问: path={}, userId={}", path, userId);
            return unauthorized(exchange, REASON_USER_REVOKED);
        }
        // REQ-SEC-023：强制改密/口令到期拦截（auth 服务侧还有第二层拦截）。
        // 置于撤销校验之后：已改密/停用的旧令牌（撤销生效）优先 401，
        // 仅对「身份有效且未撤销」的强制改密令牌返回 403
        if (Boolean.parseBoolean(stringify(claims.get(CLAIM_PWD_CHANGE_REQUIRED)))
                && !isPasswordChangeAllowed(path)) {
            log.warn("令牌携带强制改密标记，拒绝访问业务接口: path={}, userId={}", path, userId);
            return forbidden(exchange, REASON_PASSWORD_CHANGE_REQUIRED);
        }
        return forward(exchange, request, claims, tenantId, chain);
    }

    /**
     * 注入身份头并转发下游
     *
     * @param exchange 当前交换对象
     * @param request 原始请求
     * @param claims 已验签的 JWT 载荷
     * @param tenantId 令牌租户 ID
     * @param chain 下游过滤器链
     * @return 完成信号
     */
    private static Mono<Void> forward(ServerWebExchange exchange, ServerHttpRequest request, Claims claims,
                                      String tenantId, GatewayFilterChain chain) {
        ServerHttpRequest mutatedRequest = withIdentityHeaders(request, claims, tenantId);
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    /**
     * 判断令牌签发时间是否显著超前当前时间（超出 {@link #ALLOWED_CLOCK_SKEW_MILLIS}）
     *
     * @param claims 已验签的 JWT 载荷
     * @return 超前返回 {@code true}；缺失 iat 或在上限内返回 {@code false}
     */
    private static boolean isIssuedInFuture(Claims claims) {
        long issuedAtMillis = TokenTimeSupport.issuedAtMillis(claims);
        return issuedAtMillis > 0L
                && issuedAtMillis > System.currentTimeMillis() + ALLOWED_CLOCK_SKEW_MILLIS;
    }

    /**
     * 判断请求是否命中免认证白名单：本地 {@code white-list}（不限方法）或远程下发规则命中即放行。
     *
     * <p>本地配置优先短路，保证 auth 不可用时登录/刷新等关键路径始终可用。</p>
     *
     * @param path 请求路径
     * @param method 请求方法（如 {@code GET}），可空
     * @return 命中白名单返回 {@code true}
     */
    private boolean isWhiteListed(String path, String method) {
        boolean localHit = properties.getWhiteList().stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
        return localHit || accessListProvider.matchesWhitelist(path, method);
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
        return GatewayResponseWriter.write(exchange, HttpStatus.UNAUTHORIZED, 401,
                GatewayMessageResolver.resolve(exchange, "gateway.auth.unauthorized", "未认证"));
    }

    /**
     * 返回 403 路径封禁 JSON 响应（黑名单命中），提示信息为服务端固定文案
     *
     * @param exchange 当前请求上下文
     * @param reason   拒绝原因（用于指标标签）
     * @return 写出响应完成的信号
     */
    private Mono<Void> pathForbidden(ServerWebExchange exchange, String reason) {
        countRejected(reason);
        return GatewayResponseWriter.write(exchange, HttpStatus.FORBIDDEN, HttpStatus.FORBIDDEN.value(),
                GatewayMessageResolver.resolve(exchange, "gateway.auth.forbidden", "禁止访问"));
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
        return GatewayResponseWriter.write(exchange, HttpStatus.FORBIDDEN, 403,
                GatewayMessageResolver.resolve(exchange, "gateway.auth.password-change-required",
                        "首次登录或口令已到期，请先修改口令"));
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
