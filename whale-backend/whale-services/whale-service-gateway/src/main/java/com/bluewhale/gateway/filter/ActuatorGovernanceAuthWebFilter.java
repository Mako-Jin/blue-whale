package com.bluewhale.gateway.filter;

import com.bluewhale.gateway.common.GatewayResponseWriter;
import com.bluewhale.gateway.config.WhaleGatewayProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 路由治理端点鉴权过滤器（P1-13）。
 *
 * <p>{@code /actuator/**} 在 {@code JwtAuthGlobalFilter} 白名单内，且 actuator 端点不经过网关路由链，
 * 因此 {@code /actuator/routeGovernance} 的读/写操作（含路由回滚）默认处于匿名可达状态。
 * 本过滤器在 WebFlux 全局链最前置拦截该端点，要求 {@code X-Governance-Token} 与
 * {@code whale.gateway.governance.token} 一致（常量时间比较）；令牌未配置时一律 403（fail-closed）。</p>
 *
 * <p>仅拦截治理端点，health/info/prometheus 等只读观测端点保持匿名可用。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActuatorGovernanceAuthWebFilter implements WebFilter, Ordered {

    /** 治理端点令牌请求头 */
    public static final String GOVERNANCE_TOKEN_HEADER = "X-Governance-Token";

    /** 受保护的治理端点路径 */
    private static final String GOVERNANCE_PATH = "/actuator/routeGovernance";

    private final WhaleGatewayProperties properties;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        if (!GOVERNANCE_PATH.equals(path) && !path.startsWith(GOVERNANCE_PATH + "/")) {
            return chain.filter(exchange);
        }
        String expected = properties.getGovernance().getToken();
        String provided = request.getHeaders().getFirst(GOVERNANCE_TOKEN_HEADER);
        if (expected == null || expected.isBlank() || provided == null
                || !constantTimeEquals(expected, provided)) {
            log.warn("路由治理端点令牌校验失败，拒绝访问: path={}, method={}",
                    path, request.getMethod());
            return forbidden(exchange);
        }
        return chain.filter(exchange);
    }

    /**
     * 常量时间比较两个令牌。
     *
     * <p>{@link MessageDigest#isEqual(byte[], byte[])} 在 JDK 实现中会先比较长度，
     * 长度不同即提前返回，不完全常量时间。这里先对两侧做 SHA-256 摘要（固定 32 字节），
     * 再比较摘要，长度差异不再泄露时序信息。</p>
     *
     * @param expected 期望令牌
     * @param provided 客户端提供令牌
     * @return 完全一致返回 {@code true}
     */
    private static boolean constantTimeEquals(String expected, String provided) {
        return MessageDigest.isEqual(sha256(expected), sha256(provided));
    }

    /**
     * 计算字符串的 SHA-256 摘要
     *
     * @param value 原始字符串
     * @return 32 字节摘要
     */
    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            // JDK 必须提供 SHA-256，走到这里说明运行环境异常，直接失败关闭
            throw new IllegalStateException("SHA-256 摘要算法不可用", e);
        }
    }

    /**
     * 返回 403 JSON 响应，并透传链路追踪头
     *
     * @param exchange 当前请求上下文
     * @return 写出响应完成的信号
     */
    private Mono<Void> forbidden(ServerWebExchange exchange) {
        return GatewayResponseWriter.write(exchange, HttpStatus.FORBIDDEN, 403, "路由治理端点拒绝访问");
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
