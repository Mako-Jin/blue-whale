package com.bluewhale.gateway.filter;

import com.bluewhale.gateway.common.GatewayResponseWriter;
import com.bluewhale.gateway.config.RateLimitProperties;
import com.bluewhale.gateway.config.RateLimitProperties.PathRule;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
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
 * <p><b>计数与过期原子化</b>：用 Lua 脚本一次往返完成 {@code INCR} 与首计数时的 {@code EXPIRE}，
 * 避免「已 INCR 但 EXPIRE 失败」导致计数键永不过期、客户端被永久 429 的竞态。</p>
 *
 * <p><b>固定窗口固有缺陷</b>：窗口切换边界处，前一窗口末尾与新窗口开头可叠加到接近 2×limit，
 * 属固定窗口的已知取舍；阈值按此余量设置即可，若需严格平滑可后续改滑动窗口。</p>
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

    /** 窗口秒数下界，避免 0 或负数导致窗口编号除零 */
    private static final int MIN_WINDOW_SECONDS = 1;

    /**
     * 计数与过期原子化脚本：返回本次计数；首次计数（=1）或键缺失 TTL（{@code TTL < 0}）时补设窗口级 TTL。
     *
     * <p>仅判断 {@code current == 1} 不够：若键因人为 {@code SET}、或「INCR 成功但 EXPIRE 未执行」
     * 的极小窗口残留为「无 TTL 常驻键」，脚本此后永不补 TTL，该客户端会被<b>永久 429</b> 且极难排查。
     * 因此额外用 {@code TTL < 0} 兜底修复（-1=无 TTL，-2=键不存在）。</p>
     */
    private static final RedisScript<Long> INCR_WITH_EXPIRE = new DefaultRedisScript<>(
            "local current = redis.call('INCR', KEYS[1]) "
                    + "if current == 1 or redis.call('TTL', KEYS[1]) < 0 then "
                    + "redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
                    + "return current",
            Long.class);

    /** 限流拒绝计数指标名（Micrometer 推荐点号分隔） */
    private static final String METER_REJECTED = "gateway.rate.limit.rejected";

    /** 指标规则标签名 */
    private static final String TAG_RULE = "rule";

    /** 限流触发时的业务状态码 */
    private static final int RATE_LIMIT_CODE = 429;

    /** 限流触发时的提示文案 */
    private static final String RATE_LIMIT_MESSAGE = "请求过于频繁，请稍后重试";

    /**
     * 过滤器顺序：晚于链路追踪（-200），保证 429 响应也能带上 X-Trace-Id 便于关联日志；
     * 仍早于内网屏蔽（-110）与鉴权（-100），避免超限流量进入下游。
     */
    private static final int ORDER = -190;

    private final ReactiveStringRedisTemplate redisTemplate;
    private final RateLimitProperties properties;

    /** 指标注册表提供者：容器无 MeterRegistry 时返回 {@code null}，指标静默跳过 */
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    /** Ant 路径匹配器（线程安全，复用单例） */
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * 构造限流过滤器，注入响应式 Redis 模板、限流配置与指标注册表提供者
     *
     * @param redisTemplate         响应式字符串 Redis 模板
     * @param properties            网关限流配置属性
     * @param meterRegistryProvider Micrometer 指标注册表提供者，缺省时返回 {@code null} 表示不采集指标
     */
    public RateLimitGlobalFilter(ReactiveStringRedisTemplate redisTemplate, RateLimitProperties properties,
                                 ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.meterRegistryProvider = meterRegistryProvider;
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
        return redisTemplate.execute(INCR_WITH_EXPIRE, List.of(rule.key), rule.windowSeconds)
                .next()
                // 脚本异常返回空时按「窗口内首个请求」处理，避免请求悬挂
                .defaultIfEmpty(1L)
                .flatMap(count -> decide(exchange, chain, rule, count))
                .onErrorResume(e -> {
                    log.warn("网关限流计数失败（Redis 不可用），按可用性优先降级放行: client={}, key={}, reason={}",
                            clientId, rule.key, e.getMessage());
                    return chain.filter(exchange);
                });
    }

    /**
     * 依据窗口内计数值决定放行或短路：超阈值则写 429
     *
     * <p>计数键的 TTL 已由 {@link #INCR_WITH_EXPIRE} 在首次计数时原子设置，此处不再补设。</p>
     *
     * @param exchange 当前请求上下文
     * @param chain    网关过滤器链
     * @param rule     本次请求命中的限流规则快照（含计数键、阈值与窗口秒数）
     * @param count    当前窗口内累计计数，可能为 {@code null}
     * @return 继续链或写出 429 响应的完成信号
     */
    private Mono<Void> decide(ServerWebExchange exchange, GatewayFilterChain chain, LimitRule rule, Long count) {
        long current = count == null ? 0L : count;
        return current > rule.limit ? tooManyRequests(exchange, rule) : chain.filter(exchange);
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
        int configuredWindow = rule == null ? properties.getWindowSeconds() : rule.getWindowSeconds();
        // 窗口秒数必须 >= 1：0/负数会让窗口编号除零（ArithmeticException）；非法配置回落到 1 秒窗口
        int windowSeconds = configuredWindow < MIN_WINDOW_SECONDS ? MIN_WINDOW_SECONDS : configuredWindow;
        if (configuredWindow < MIN_WINDOW_SECONDS) {
            log.warn("限流窗口秒数配置非法，已回落为 1 秒: rule={}, configured={}", ruleKey, configuredWindow);
        }
        long window = System.currentTimeMillis() / (windowSeconds * MILLIS_PER_SECOND);
        String key = properties.getKeyPrefix() + KEY_SEPARATOR + ruleKey + KEY_SEPARATOR + window
                + KEY_SEPARATOR + clientId;
        return new LimitRule(ruleKey, key, limit, windowSeconds);
    }

    /**
     * 解析客户端标识：优先 X-Forwarded-For 首段，其次远端地址 host，最后退化为 unknown
     *
     * @param exchange 当前请求上下文
     * @return 客户端标识
     */
    private String resolveClientId(ServerWebExchange exchange) {
        // X-Forwarded-For 仅在明确存在可信前置代理时启用（默认开启，由 Nginx 覆写）；
        // 网关直接暴露时置 whale.gateway.rate-limit.trust-forwarded-for=false 防止伪造绕过
        if (properties.isTrustForwardedFor()) {
            String forwardedFor = exchange.getRequest().getHeaders().getFirst(X_FORWARDED_FOR_HEADER);
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                return forwardedFor.split(FORWARDED_FOR_SEPARATOR)[0].trim();
            }
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
     * @param rule     本次命中的限流规则（用于指标规则标签）
     * @return 响应写出完成的信号，不进入下游过滤器链
     */
    private Mono<Void> tooManyRequests(ServerWebExchange exchange, LimitRule rule) {
        countRejected(rule);
        return GatewayResponseWriter.write(exchange, HttpStatus.TOO_MANY_REQUESTS,
                RATE_LIMIT_CODE, RATE_LIMIT_MESSAGE);
    }

    /**
     * 记录一次限流拒绝计数（容器无 {@link MeterRegistry} 时静默跳过）
     *
     * @param rule 本次命中的限流规则
     */
    private void countRejected(LimitRule rule) {
        MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.counter(METER_REJECTED, TAG_RULE, rule.ruleKey).increment();
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

        /** 规则标识（路径模式或 default），用于指标标签 */
        private final String ruleKey;

        /** Redis 限流计数键（含规则标识与窗口维度） */
        private final String key;

        /** 本规则单个窗口内允许的最大请求数 */
        private final int limit;

        /** 本规则固定窗口长度（秒） */
        private final int windowSeconds;

        /**
         * 构造限流规则快照
         *
         * @param ruleKey 规则标识
         * @param key 计数键
         * @param limit 阈值
         * @param windowSeconds 窗口秒数
         */
        private LimitRule(String ruleKey, String key, int limit, int windowSeconds) {
            this.ruleKey = ruleKey;
            this.key = key;
            this.limit = limit;
            this.windowSeconds = windowSeconds;
        }
    }
}
