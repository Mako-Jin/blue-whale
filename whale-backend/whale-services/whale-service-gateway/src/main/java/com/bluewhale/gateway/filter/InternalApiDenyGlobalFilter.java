package com.bluewhale.gateway.filter;

import com.bluewhale.gateway.common.GatewayResponseWriter;
import com.bluewhale.gateway.config.InternalApiDenyProperties;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 内网接口屏蔽全局过滤器（T6）。
 *
 * <p>对 {@code whale.gateway.internal-deny.patterns} 列的路径模式（Ant 通配）实施外部访问拦截：
 * 命中即直接短路写出 HTTP 403 与与业务侧 {@code Result} 同结构的 JSON 体，不进入下游过滤器链
 * （写法对齐 {@code RateLimitGlobalFilter} 的 429 短路，并透传链路追踪头）。</p>
 *
 * <p>过滤器顺序置于鉴权（{@code JwtAuthGlobalFilter} order=-100）之前，确保内网路径即使未携带
 * 合法令牌也直接以 403 拒绝，而非回落为 401。</p>
 *
 * <p>GW-06（2026-11-09 收口）：默认模式为「单段星号 + /internal/」通配（星号不跨段），
 * 覆盖全部服务的 {@code /<service>/internal/**} 内网端点；新服务内网接口按该约定命名即自动纳管，
 * 无需改动本过滤器与路由代码（{@code discovery.locator} 的 {@code /whale-file/file/internal/**}
 * 服务名前缀形态由 {@link #stripServicePrefix} 剥前缀兜底，V-01）。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class InternalApiDenyGlobalFilter implements GlobalFilter, Ordered {

    /** 内网接口拦截时的业务状态码 */
    private static final int DENY_CODE = 403;

    /** 内网接口拦截时的提示文案 */
    private static final String DENY_MESSAGE = "内网接口不允许外部访问";

    /** 过滤器顺序：早于鉴权（-100）与权限码头（-50），保证内网路径先被拒绝 */
    private static final int ORDER = -110;

    /** 内网接口拒绝计数指标名 */
    private static final String METER_REJECTED = "gateway.internal_deny.rejected";

    private final InternalApiDenyProperties properties;

    /** 指标注册表提供者：容器无 MeterRegistry 时返回 {@code null}，指标静默跳过 */
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    /** Ant 路径匹配器（线程安全，复用单例） */
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * 构造内网接口屏蔽过滤器
     *
     * @param properties            内网接口屏蔽配置
     * @param meterRegistryProvider Micrometer 指标注册表提供者，缺省时返回 {@code null} 表示不采集指标
     */
    public InternalApiDenyGlobalFilter(InternalApiDenyProperties properties,
                                       ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.properties = properties;
        this.meterRegistryProvider = meterRegistryProvider;
    }

    /**
     * 内网路径拦截：命中配置的路径模式时短路返回 403，否则放行
     *
     * @param exchange 当前请求上下文
     * @param chain    网关过滤器链
     * @return 响应写出的完成信号
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }
        String path = exchange.getRequest().getPath().value();
        if (isDenied(path)) {
            log.warn("内网接口拒绝外部访问: path={}", path);
            return denied(exchange);
        }
        return chain.filter(exchange);
    }

    /**
     * 判断请求路径是否命中内网接口屏蔽模式
     *
     * <p>除原始路径外，同时校验「剥离一级服务名前缀」后的路径（V-01/T-01）：开启
     * {@code discovery.locator} 后，{@code /whale-file/file/internal/**} 这类
     * 服务名前缀形态经路由 {@code RewritePath} 还原为 {@code /file/internal/**} 直达下游，
     * 仅匹配原始路径会被绕过。</p>
     *
     * @param path 请求路径
     * @return 原始路径或剥离服务名前缀后的路径命中任一模式时返回 {@code true}
     */
    private boolean isDenied(String path) {
        if (matchesAnyPattern(path)) {
            return true;
        }
        String stripped = stripServicePrefix(path);
        return stripped != null && matchesAnyPattern(stripped);
    }

    /**
     * 剥离一级路径段（服务名前缀）：{@code /whale-file/file/internal/1} → {@code /file/internal/1}
     *
     * <p>仅当路径至少包含两个段时剥离；无前缀可剥时返回 {@code null}。</p>
     *
     * @param path 请求路径
     * @return 剥离一级前缀后的路径；无前缀可剥返回 {@code null}
     */
    private String stripServicePrefix(String path) {
        if (path == null) {
            return null;
        }
        int first = path.indexOf('/', 1);
        if (first < 0) {
            return null;
        }
        return path.substring(first);
    }

    /**
     * 路径是否命中任一屏蔽模式
     *
     * @param path 请求路径
     * @return 命中返回 {@code true}
     */
    private boolean matchesAnyPattern(String path) {
        List<String> patterns = properties.getPatterns();
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
     * 写出 403 内网接口拒绝响应（与业务侧 Result 同结构 JSON 体），并透传链路追踪头
     *
     * @param exchange 当前请求上下文
     * @return 响应写出完成的信号，不进入下游过滤器链
     */
    private Mono<Void> denied(ServerWebExchange exchange) {
        MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
        if (meterRegistry != null) {
            meterRegistry.counter(METER_REJECTED).increment();
        }
        return GatewayResponseWriter.write(exchange, HttpStatus.FORBIDDEN, DENY_CODE, DENY_MESSAGE);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
