package com.bluewhale.gateway.filter;

import com.bluewhale.gateway.common.GatewayHeaders;
import com.bluewhale.gateway.config.PermissionHeaderProperties;
import com.bluewhale.security.contract.GatewayContract;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 权限码头注入全局过滤器（AUTH-06，Wave 5.10）。
 *
 * <p>链路：{@code JwtAuthGlobalFilter}(order=-100) 验签并注入 {@code X-User-Id} 后，本过滤器
 * (order=-80) 解析当前用户权限码并注入权限码头；顺序晚于鉴权、早于熔断计时（-75）与业务转发。</p>
 *
 * <p><b>为何 order 必须早于熔断过滤器（-75）</b>：熔断过滤器用 {@code chain.filter(exchange).timeout(...)}
 * 覆盖其后所有 GlobalFilter。若本过滤器排在其后，权限码 Redis 读取与 {@code /auth/me} 回落
 * （默认 800ms）会被计入下游调用耗时；auth 抖动时「800ms 回落 + 正常下游处理」极易撞上 3000ms
 * 熔断超时，把一次 auth 慢响应误判为下游不可用并打开熔断。放在 -80（仍晚于 -100 的 JWT 注入）
 * 即可让权限码头注入在熔断计时之外完成。</p>
 *
 * <p>解析优先级：先读 Redis 缓存键 {@code whale:{tenantId}:cache:auth:user:perm:{userId}}（值为权限码逗号串，
 * 空集合写成哨兵 {@code __NULL__}）；未命中 / 命中哨兵 / 读取异常时回落
 * {@code GET lb://whale-service-auth/auth/me}（带原请求 Authorization），解析 {@code data.permissions} 数组。</p>
 *
 * <p><b>fail-closed</b>：两级解析均失败时注入<b>空串</b>并仅告警（不抛异常），下游 whale-audit 的
 * {@code RequirePermissionInterceptor} 将据此拒绝受保护查询。</p>
 *
 * <p><b>为何在网关自定义头常量而不复用 starter 的 {@code com.bluewhale.common.web.WebHeaders}</b>：
 * whale-service-gateway 为 WebFlux 响应式栈，引入 {@code whale-spring-boot-starter}（Servlet/WebMvc）会与
 * WebFlux 冲突；故此处独立定义头常量，属可接受的跨栈重复。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class PermissionHeaderGlobalFilter implements GlobalFilter, Ordered {

    /** 用户ID请求头（由 JwtAuthGlobalFilter 注入，网关内统一取自 {@link GatewayHeaders}） */
    private static final String USER_ID_HEADER = GatewayHeaders.USER_ID;

    /** Redis Key 分段分隔符 */
    private static final String KEY_SEPARATOR = ":";

    /** 权限码缓存名（与 whale-service-auth 的 PermissionCache.PERMISSION_CACHE_NAME 对齐） */
    private static final String PERMISSION_CACHE_NAME = "auth:user:perm";

    /** 缺失租户头时的未知租户段 */
    private static final String UNKNOWN_TENANT = "0";

    /** 回落拉取权限码的服务地址（lb:// 由负载均衡 WebClient 解析） */
    private static final String AUTH_ME_URI = "lb://whale-service-auth/auth/v1/me";

    /** /auth/me 响应体中的业务数据字段名 */
    private static final String FIELD_DATA = GatewayContract.Envelope.DATA;

    /** /auth/me 响应体中的权限码数组字段名 */
    private static final String FIELD_PERMISSIONS = GatewayContract.CurrentUser.PERMISSIONS;

    /** 权限码拼接分隔符 */
    private static final String CODES_SEPARATOR = ",";

    /** 过滤器顺序：晚于 JwtAuthGlobalFilter(-100)、早于熔断计时(-75)与业务转发 */
    private static final int ORDER = -80;

    private final PermissionHeaderProperties properties;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    /**
     * 构造权限码头注入过滤器
     *
     * @param properties       权限码头注入配置
     * @param redisTemplate    响应式字符串 Redis 模板（WebFlux 栈不可用阻塞式客户端）
     * @param webClientBuilder 负载均衡 WebClient 构造器，用于回落 {@code /auth/me}
     * @param objectMapper     Jackson 3 对象映射器，用于解析 {@code /auth/me} 响应
     */
    public PermissionHeaderGlobalFilter(PermissionHeaderProperties properties,
                                        ReactiveStringRedisTemplate redisTemplate,
                                        @Qualifier("loadBalancedWebClientBuilder") WebClient.Builder webClientBuilder,
                                        ObjectMapper objectMapper) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
    }

    /**
     * 解析并注入权限码头：未认证请求与关闭开关时直接放行，不注入。
     *
     * @param exchange 当前请求上下文
     * @param chain    网关过滤器链
     * @return 响应写出的完成信号
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.isEnabled()) {
            // 关闭注入时仍需移除外部伪造的权限码头，避免下游误信客户端声明的权限
            return chain.filter(withoutCodesHeader(exchange));
        }
        ServerHttpRequest request = exchange.getRequest();
        String userId = request.getHeaders().getFirst(USER_ID_HEADER);
        if (userId == null || userId.isBlank()) {
            // 未认证请求（白名单）：同样移除伪造的权限码头，交由下游鉴权/白名单处理
            return chain.filter(withoutCodesHeader(exchange));
        }
        return resolveCodes(request, userId)
                .flatMap(codes -> chain.filter(inject(exchange, codes)));
    }

    /**
     * 移除入站权限码头（用于不注入权限码的请求）
     *
     * @param exchange 当前请求上下文
     * @return 移除权限码头后的新请求上下文
     */
    private ServerWebExchange withoutCodesHeader(ServerWebExchange exchange) {
        String headerName = properties.getCodesHeader();
        return exchange.mutate()
                .request(request -> request.headers(headers -> headers.remove(headerName)))
                .build();
    }

    /**
     * 解析权限码：先读 Redis 缓存，未命中 / 命中哨兵 / 异常时回落 {@code /auth/me}，两级均失败返回空串。
     *
     * @param request 原始请求（透传 Authorization 至回落调用）
     * @param userId  当前用户 ID
     * @return 权限码逗号串；两级解析均失败时为空串
     */
    private Mono<String> resolveCodes(ServerHttpRequest request, String userId) {
        String tenantId = request.getHeaders().getFirst(properties.getTenantIdHeader());
        String tenantSegment = tenantId == null || tenantId.isBlank() ? UNKNOWN_TENANT : tenantId.trim();
        String key = properties.getCacheKeyPrefix() + KEY_SEPARATOR + tenantSegment + KEY_SEPARATOR
                + "cache" + KEY_SEPARATOR + PERMISSION_CACHE_NAME + KEY_SEPARATOR + userId;
        Mono<String> cached = redisTemplate.opsForValue().get(key)
                .filter(this::isCacheHit)
                .onErrorResume(e -> {
                    log.warn("读取权限码缓存失败，回落 /auth/me: userId={}, reason={}", userId, e.getMessage());
                    return Mono.empty();
                });
        return cached
                .switchIfEmpty(Mono.defer(() -> fallbackFromAuthMe(request, userId)))
                .defaultIfEmpty("");
    }

    /**
     * 判断缓存值是否为有效命中：非 {@code null} 且不等于空值哨兵
     *
     * @param cached Redis 缓存原始值，可能为 {@code null}
     * @return 有效命中返回 {@code true}
     */
    private boolean isCacheHit(String cached) {
        return cached != null && !properties.getNullSentinel().equals(cached);
    }

    /**
     * 回落调用 {@code lb://whale-service-auth/auth/me}，解析 {@code data.permissions} 拼接为权限码串。
     *
     * <p>调用或解析失败时仅告警并返回空（由上游 {@code defaultIfEmpty("")} 归为空串），保证 fail-closed。</p>
     *
     * @param request 原始请求，用于透传 Authorization 头
     * @param userId  当前用户 ID，仅用于告警定位
     * @return 权限码逗号串；失败时发出空信号
     */
    private Mono<String> fallbackFromAuthMe(ServerHttpRequest request, String userId) {
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        // Wave 5.53 真机复测修复：/auth/me 依赖租户上下文（TenantContextFilter 强制 X-Tenant-Id），
        // 缺失时 whale-service-auth 返回 HTTP 200 信封 + code=400 + data=null，parsePermissions 静默解析为空串，
        // 导致权限码头被注入为空、下游受限知识块被全部过滤；此处透传网关注入的租户头。
        String tenantId = request.getHeaders().getFirst(properties.getTenantIdHeader());
        return webClientBuilder.build()
                .get()
                .uri(AUTH_ME_URI)
                .headers(headers -> {
                    if (authorization != null && !authorization.isBlank()) {
                        headers.set(HttpHeaders.AUTHORIZATION, authorization);
                    }
                    if (tenantId != null && !tenantId.isBlank()) {
                        headers.set(properties.getTenantIdHeader(), tenantId.trim());
                    }
                })
                .retrieve()
                .bodyToMono(String.class)
                // 强制超时：auth 抖动时快速失败，避免每个请求都挂在回落调用上（fail-closed）
                .timeout(Duration.ofMillis(Math.max(1L, properties.getFallbackTimeoutMillis())))
                .map(this::parsePermissions)
                .onErrorResume(e -> {
                    log.warn("回落 /auth/me 获取权限码失败，注入空权限码头（fail-closed）: userId={}, reason={}",
                            userId, e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * 解析 {@code /auth/me} 响应体，提取 {@code data.permissions} 并拼接为权限码串。
     *
     * <p>仅用 Jackson 的 {@code readValue} 反序列化为 Map（不触碰 Jackson 2/3 存在差异的树模型 API）；
     * 解析失败仅告警并返回空串（fail-closed）。</p>
     *
     * @param body 响应体 JSON 文本，可能为 {@code null} 或空
     * @return 权限码逗号串；无权限或解析失败时为空串
     */
    String parsePermissions(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            Map<?, ?> root = objectMapper.readValue(body, Map.class);
            Object data = root.get(FIELD_DATA);
            Object permissions = data instanceof Map<?, ?> dataMap ? dataMap.get(FIELD_PERMISSIONS) : null;
            if (!(permissions instanceof Collection<?> collection)) {
                return "";
            }
            List<String> codes = new ArrayList<>();
            for (Object item : collection) {
                if (item == null) {
                    continue;
                }
                String code = String.valueOf(item).trim();
                if (!code.isEmpty()) {
                    codes.add(code);
                }
            }
            return String.join(CODES_SEPARATOR, codes);
        } catch (Exception e) {
            log.warn("解析 /auth/me 权限码响应失败，注入空权限码头（fail-closed）: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 注入权限码头：先移除外部伪造的同名头，再写入解析出的权限码。
     *
     * @param exchange 当前请求上下文
     * @param codes    权限码逗号串（可为空串，表示无权限）
     * @return 注入权限码头后的新请求上下文
     */
    private ServerWebExchange inject(ServerWebExchange exchange, String codes) {
        String headerName = properties.getCodesHeader();
        return exchange.mutate()
                .request(request -> request.headers(headers -> {
                    headers.remove(headerName);
                    headers.set(headerName, codes);
                }))
                .build();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
