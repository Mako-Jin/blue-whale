package com.bluewhale.gateway.filter;

import com.bluewhale.gateway.common.GatewayMessageResolver;
import com.bluewhale.gateway.common.GatewayResponseWriter;
import com.bluewhale.gateway.config.WhaleGatewayProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * WebSocket 握手 ticket 前置校验过滤器（T19 / M2）。
 *
 * <p>Spring Cloud Gateway 的 WebSocket 路由会先与客户端完成协议升级，再连接后端；
 * 若只依赖下游服务握手拦截，缺失 ticket 的客户端仍会短暂看到 101。</p>
 *
 * <p>本过滤器在协议升级之前检查 {@code whale.gateway.ws-ticket.paths} 配置的 WS 握手路径
 * （默认 {@code /im/ws}、{@code /im/world}、{@code /doc/ws}）的 {@code ticket} 查询参数：
 * 缺失或空白直接返回 401，不进入 {@code WebsocketRoutingFilter}（其 order 为
 * {@link Ordered#LOWEST_PRECEDENCE} 附近）。ticket 的存在性、有效期与一次性仍由
 * 下游服务端原子消费判定，网关不做任何消费动作。</p>
 *
 * <p><b>为何路径改为配置项（P2-6）</b>：同一组 WS 路径此前硬编码在过滤器、{@code white-list}
 * 与兜底路由 predicates 三处，新增端点漏改过滤器就会出现「能连上但下游永远拒绝」。
 * 现在过滤器读取 {@code whale.gateway.ws-ticket.paths}；新增 WS 端点时仍需同步
 * {@code white-list}（WS 无法带 Authorization）与路由 predicates，配置注释已标注。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class ImWsTicketGuardGlobalFilter implements GlobalFilter, Ordered {

    /** 一次性凭证查询参数名 */
    private static final String TICKET_PARAM = "ticket";

    /** 过滤器顺序：晚于 JWT 鉴权（-100），早于协议升级 */
    private static final int ORDER = -95;

    private final WhaleGatewayProperties properties;

    /**
     * 构造 WS ticket 校验过滤器
     *
     * @param properties 网关配置（读取 {@code ws-ticket.paths}）
     */
    public ImWsTicketGuardGlobalFilter(WhaleGatewayProperties properties) {
        this.properties = properties;
    }

    /**
     * 校验 WS 握手是否携带 ticket，其余路径与已携带 ticket 的请求放行。
     *
     * @param exchange 当前请求上下文
     * @param chain    网关过滤器链
     * @return 响应写出或下游链完成信号
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (!isWsPath(request.getURI().getPath())) {
            return chain.filter(exchange);
        }
        String ticket = request.getQueryParams().getFirst(TICKET_PARAM);
        if (ticket == null || ticket.isBlank()) {
            log.warn("WS 握手缺少 ticket，网关拒绝升级: remote={}", request.getRemoteAddress());
            return unauthorized(exchange);
        }
        return chain.filter(exchange);
    }

    /**
     * 判断请求路径是否属于需要 ticket 的 WS 握手端点（精确匹配）
     *
     * @param path 请求路径
     * @return 命中配置列表返回 {@code true}
     */
    private boolean isWsPath(String path) {
        List<String> paths = properties.getWsTicket().getPaths();
        return paths != null && paths.contains(path);
    }

    /**
     * 写出 401 未认证响应（与业务侧 Result 同结构），并透传链路追踪头。
     *
     * @param exchange 当前请求上下文
     * @return 响应写出完成的信号
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        return GatewayResponseWriter.write(exchange, HttpStatus.UNAUTHORIZED, 401,
                GatewayMessageResolver.resolve(exchange, "gateway.auth.unauthorized", "未认证"));
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
