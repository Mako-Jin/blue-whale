package com.bluewhale.gateway.filter;

import com.bluewhale.gateway.common.GatewayResponse;
import com.bluewhale.gateway.config.RateLimitProperties;
import com.bluewhale.gateway.config.RateLimitProperties.PathRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * 网关入口限流过滤器（T5.4 / AUTH-30）：Redis 计数器 + 固定窗口。
 * <p>键格式 {@code {keyPrefix}:{规则标识}:{窗口编号}:{客户端标识}}；计数超过阈值时短路返回 HTTP 429 与
 * {@code Result} 同结构 JSON 体，不进入下游过滤器链。</p>
 * <p>规则选取：{@code whale.gateway.rate-limit.path-rules} 中以 {@link AntPathMatcher} 取最长匹配的
 * 路径规则（特化优先），未命中则用全局阈值；规则标识维度避免不同规则窗口互相污染。</p>
 * <p>降级策略：Redis 不可用时仅告警并放行，避免限流存储故障阻断全站流量
 * （与令牌黑名单「可用性优先」取舍一致）。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class RateLimitGlobalFilter implements GlobalFilter, Ordered {

    /** 客户端真实 IP 透传头名 */
    private static final String X_FORWARDED_FOR_HEADER = "X-Forwarded-For";

    /** X-Forwarded-For 多级代理分隔符，取首段为最上游客户端 */
    private static final String FORWARDED_FOR_SEPARATOR = ",";

    /** 限流键分段分隔符 */
    private static final String KEY_SEPARATOR = ":";

    /** 无法解析客户端标识时的退化值 */
    private static final String UNKNOWN_CLIENT = "unknown";

    /** 未命中任何路径规则时的规则标识（沿用全局阈值） */
    private static final String DEFAULT_RULE_KEY = "default";

    /** 每秒毫秒数，用于将窗口秒数换算为毫秒 */
    private static final long MILLIS_PER_SECOND = 1000L;

    /** 单次计数值：出现该值说明是本窗口首次请求，需为键设置过期 */
    private static final long FIRST_COUNT = 1L;

    /** 限流触发时的业务状态码 */
    private static final int RATE_LIMIT_CODE = 429;

    /** 限流触发时的提示文案 */
    private static final String RATE_LIMIT_MESSAGE = "请求过于频繁，请稍后重试";

    /** JSON 响应内容类型 */
    private static final String JSON_CONTENT_TYPE = "application/json;charset=UTF-8";

    /** 过滤器顺序：先于链路追踪与鉴权执行，避免未限流流量进入下游 */
    private static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

    private final ReactiveStringRedisTemplate redisTemplate;
    private final RateLimitProperties properties;

    /** Ant 路径匹配器（线程安全，复用单例） */
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * 构造限流过滤器，注入响应式 Redis 模板与限流配置
     *
     * @param redisTemplate 响应式字符串 Redis 模板
     * @param properties    网关限流配置属性
     */
    public RateLimitGlobalFilter(ReactiveStringRedisTemplate redisTemplate, RateLimitProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    /**
     * 入口限流：对当前窗口内客户端请求计数，超阈值短路，否则放行
     *
     * @param exchange 当前请求上下文
     * @param chain    网关过滤器链
     * @return 响应写出的完成信号；计数未超阈值时继续链，超阈值时写 429，Redis 异常时降级放行
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }
        String clientId = resolveClientId(exchange);
        LimitRule rule = resolveRule(matchRule(exchange.getRequest().getPath().value()), clientId);
        return redisTemplate.opsForValue().increment(rule.key)
                .flatMap(count -> decide(exchange, chain, rule, count))
                .onErrorResume(e -> {
                    log.warn("网关限流计数失败（Redis 不可用），按可用性优先降级放行: client={}, key={}, reason={}",
                            clientId, rule.key, e.getMessage());
                    return chain.filter(exchange);
                });
    }

    /**
     * 依据窗口内计数值决定放行或短路：首次计数时补设键过期，超阈值则写 429
     *
     * @param exchange 当前请求上下文
     * @param chain    网关过滤器链
     * @param rule     本次请求命中的限流规则快照（含计数键、阈值与窗口秒数）
     * @param count    当前窗口内累计计数，可能为 {@code null}
     * @return 继续链或写出 429 响应的完成信号
     */
    private Mono<Void> decide(ServerWebExchange exchange, GatewayFilterChain chain, LimitRule rule, Long count) {
        long current = count == null ? 0L : count;
        Mono<Boolean> expiration = current == FIRST_COUNT
                ? redisTemplate.expire(rule.key, Duration.ofSeconds(rule.windowSeconds))
                        .defaultIfEmpty(Boolean.TRUE)
                : Mono.just(Boolean.TRUE);
        return expiration.flatMap(ignored -> current > rule.limit
                ? tooManyRequests(exchange)
                : chain.filter(exchange));
    }

    /**
     * 在 {@code pathRules} 中选取最长匹配的路径规则（特化优先）
     *
     * @param path 请求路径
     * @return 命中的路径规则；无规则配置或均未命中时返回 {@code null}
     */
    private PathRule matchRule(String path) {
        List<PathRule> rules = properties.getPathRules();
        if (rules == null || rules.isEmpty()) {
            return null;
        }
        PathRule matched = null;
        for (PathRule rule : rules) {
            if (rule == null || rule.getPathPattern() == null) {
                continue;
            }
            if (!pathMatcher.match(rule.getPathPattern(), path)) {
                continue;
            }
            if (matched == null || rule.getPathPattern().length() > matched.getPathPattern().length()) {
                matched = rule;
            }
        }
        return matched;
    }

    /**
     * 构建限流规则快照：命中规则以其标识/阈值/窗口为准，未命中沿用全局配置
     *
     * @param rule 命中的路径规则，可能为 {@code null}
     * @param clientId 客户端标识
     * @return 限流规则快照（含计数键）
     */
    private LimitRule resolveRule(PathRule rule, String clientId) {
        String ruleKey = rule == null ? DEFAULT_RULE_KEY : rule.getPathPattern();
        int limit = rule == null ? properties.getLimit() : rule.getLimit();
        int windowSeconds = rule == null ? properties.getWindowSeconds() : rule.getWindowSeconds();
        long window = System.currentTimeMillis() / (windowSeconds * MILLIS_PER_SECOND);
        String key = properties.getKeyPrefix() + KEY_SEPARATOR + ruleKey + KEY_SEPARATOR + window
                + KEY_SEPARATOR + clientId;
        return new LimitRule(key, limit, windowSeconds);
    }

    /**
     * 解析客户端标识：优先 X-Forwarded-For 首段，其次远端地址 host，最后退化为 unknown
     *
     * @param exchange 当前请求上下文
     * @return 客户端标识
     */
    private String resolveClientId(ServerWebExchange exchange) {
        String forwardedFor = exchange.getRequest().getHeaders().getFirst(X_FORWARDED_FOR_HEADER);
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(FORWARDED_FOR_SEPARATOR)[0].trim();
        }
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getHostString() != null) {
            return remoteAddress.getHostString();
        }
        return UNKNOWN_CLIENT;
    }

    /**
     * 写出 429 限流响应（与业务侧 Result 同结构 JSON 体），并透传链路追踪头
     *
     * @param exchange 当前请求上下文
     * @return 响应写出完成的信号，不进入下游过滤器链
     */
    private Mono<Void> tooManyRequests(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.parseMediaType(JSON_CONTENT_TYPE));
        String traceId = exchange.getRequest().getHeaders().getFirst(TraceIdGlobalFilter.TRACE_ID_HEADER);
        if (traceId != null && !traceId.isBlank()) {
            response.getHeaders().set(TraceIdGlobalFilter.TRACE_ID_HEADER, traceId);
        }
        String body = GatewayResponse.of(RATE_LIMIT_CODE, RATE_LIMIT_MESSAGE).toJson();
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    /**
     * 单次限流判定所需的规则快照：计数键 + 阈值 + 窗口秒数。
     *
     * @author BlueWhale
     */
    private static final class LimitRule {

        /** Redis 限流计数键（含规则标识与窗口维度） */
        private final String key;

        /** 本规则单个窗口内允许的最大请求数 */
        private final int limit;

        /** 本规则固定窗口长度（秒） */
        private final int windowSeconds;

        /**
         * 构造限流规则快照
         *
         * @param key 计数键
         * @param limit 阈值
         * @param windowSeconds 窗口秒数
         */
        private LimitRule(String key, int limit, int windowSeconds) {
            this.key = key;
            this.limit = limit;
            this.windowSeconds = windowSeconds;
        }
    }
}
