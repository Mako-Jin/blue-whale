package com.bluewhale.gateway.filter;

import com.bluewhale.gateway.common.GatewayResponseWriter;
import com.bluewhale.gateway.config.WhaleGatewayProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * 管理端点内网访问控制过滤器（P2-14）。
 *
 * <p>{@code /actuator/**} 不经过网关路由链，{@code JwtAuthGlobalFilter} 的白名单对其无效；
 * 此前仅 {@code routeGovernance} 有令牌校验，{@code health/info/prometheus} 可被公网匿名读取
 * （含 JVM、线程、指标等运行态信息）。仓库内没有 Nginx 配置，无法直接按运维建议收口，
 * 因此在网关 WebFilter 链最前置增加等价限制。</p>
 *
 * <p><b>双重来源校验</b>：</p>
 * <ol>
 *   <li>直连远端地址必须是内网/回环——阻断公网直接访问网关端口；</li>
 *   <li>存在 {@code X-Forwarded-For} 时，其首段客户端地址也必须是内网——阻断公网请求
 *       经 Nginx 转发后「远端地址变成 Nginx 内网 IP」的绕过路径。</li>
 * </ol>
 *
 * <p>内网判定：回环、IPv4 站点本地（10/8、172.16/12、192.168/16）、链路本地、
 * IPv6 唯一本地地址（{@code fc00::/7}）。只解析 IP 字面量，不做 DNS 查询，避免事件循环阻塞。</p>
 *
 * <p>开关 {@code whale.gateway.actuator.internal-only}（默认 {@code true}）：仅当网络层
 * 已隔离 {@code /actuator} 时才可关闭。治理端点 {@code routeGovernance} 的
 * {@code X-Governance-Token} 校验（{@link ActuatorGovernanceAuthWebFilter}）保持不变。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class ActuatorInternalOnlyWebFilter implements WebFilter, Ordered {

    /** actuator 根路径 */
    private static final String ACTUATOR_PATH = "/actuator";

    /** 客户端真实 IP 透传头名 */
    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    /** 多级代理分隔符 */
    private static final String FORWARDED_FOR_SEPARATOR = ",";

    /** IPv4 字面量格式 */
    private static final Pattern IPV4_LITERAL = Pattern.compile("^\\d{1,3}(?:\\.\\d{1,3}){3}$");

    /** IPv6 字面量字符集（是否合法最终交给 {@link InetAddress#getByName}） */
    private static final Pattern IPV6_LITERAL = Pattern.compile("^[0-9A-Fa-f:.]+$");

    /** IPv6 唯一本地地址首字节掩码：{@code fc00::/7} */
    private static final int ULA_MASK = 0xFE;

    /** IPv6 唯一本地地址首字节特征：{@code fc} / {@code fd} */
    private static final int ULA_PREFIX = 0xFC;

    private final WhaleGatewayProperties properties;

    /**
     * 构造管理端点访问控制过滤器
     *
     * @param properties 网关配置（读取 {@code actuator.internal-only}）
     */
    public ActuatorInternalOnlyWebFilter(WhaleGatewayProperties properties) {
        this.properties = properties;
    }

    /**
     * 对 {@code /actuator/**} 做来源校验：非内网来源返回 403，其余（含非 actuator 路径）放行。
     *
     * @param exchange 当前请求上下文
     * @param chain    WebFilter 链
     * @return 响应写出或后续链完成信号
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!properties.getActuator().isInternalOnly()) {
            return chain.filter(exchange);
        }
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        if (!isActuatorPath(path)) {
            return chain.filter(exchange);
        }
        if (isTrustedSource(request)) {
            return chain.filter(exchange);
        }
        log.warn("管理端点拒绝外部访问: path={}, remote={}, forwardedFor={}", path,
                request.getRemoteAddress(), request.getHeaders().getFirst(FORWARDED_FOR_HEADER));
        return GatewayResponseWriter.write(exchange, HttpStatus.FORBIDDEN, 403, "管理端点仅限内网访问");
    }

    /**
     * 判断路径是否属于 actuator 端点（{@code /actuator} 本身或其子路径）
     *
     * @param path 请求路径
     * @return 命中返回 {@code true}
     */
    private static boolean isActuatorPath(String path) {
        return ACTUATOR_PATH.equals(path) || path.startsWith(ACTUATOR_PATH + "/");
    }

    /**
     * 判断请求来源是否可信：直连地址与 {@code X-Forwarded-For} 首段都必须是内网。
     *
     * @param request 当前请求
     * @return 可信返回 {@code true}
     */
    private static boolean isTrustedSource(ServerHttpRequest request) {
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null
                || !isInternalAddress(remoteAddress.getAddress())) {
            return false;
        }
        String forwardedFor = request.getHeaders().getFirst(FORWARDED_FOR_HEADER);
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return true;
        }
        InetAddress clientAddress = parseLiteral(forwardedFor.split(FORWARDED_FOR_SEPARATOR)[0].trim());
        return clientAddress != null && isInternalAddress(clientAddress);
    }

    /**
     * 解析 IP 字面量（仅 IPv4/IPv6 字面量，不做 DNS 解析）
     *
     * @param value 地址文本，可能为 {@code [IPv6]} 形式
     * @return 解析后的地址；非字面量或非法返回 {@code null}
     */
    private static InetAddress parseLiteral(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String candidate = value;
        int closingBracket = candidate.indexOf(']');
        if (candidate.startsWith("[") && closingBracket > 0) {
            candidate = candidate.substring(1, closingBracket);
        }
        boolean ipv4 = IPV4_LITERAL.matcher(candidate).matches();
        boolean ipv6 = candidate.indexOf(':') >= 0 && IPV6_LITERAL.matcher(candidate).matches();
        if (!ipv4 && !ipv6) {
            return null;
        }
        try {
            return InetAddress.getByName(candidate);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    /**
     * 判断地址是否属于内网范围（回环 / 站点本地 / 链路本地 / IPv6 ULA）
     *
     * @param address 待判定地址
     * @return 内网返回 {@code true}
     */
    private static boolean isInternalAddress(InetAddress address) {
        if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & ULA_MASK) == ULA_PREFIX;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
