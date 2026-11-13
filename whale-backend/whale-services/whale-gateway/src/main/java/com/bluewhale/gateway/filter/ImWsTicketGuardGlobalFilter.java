package com.bluewhale.gateway.filter;

import com.bluewhale.gateway.common.GatewayResponse;
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
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * WebSocket 握手 ticket 前置校验过滤器（T19 / M2）。
 *
 * <p>Spring Cloud Gateway 的 WebSocket 路由会先与客户端完成协议升级，再连接后端；
 * 若只依赖下游服务握手拦截，缺失 ticket 的客户端仍会短暂看到 101。</p>
 *
 * <p>本过滤器在协议升级之前检查 {@code /im/ws} 与 {@code /doc/ws} 的 {@code ticket}
 * 查询参数：缺失或空白直接返回 401，不进入 {@code WebsocketRoutingFilter}（其 order 为
 * {@link Ordered#LOWEST_PRECEDENCE} 附近）。ticket 的存在性、有效期与一次性仍由
 * 下游服务端原子消费判定，网关不做任何消费动作。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class ImWsTicketGuardGlobalFilter implements GlobalFilter, Ordered {

    /**
     * 需要 ticket 的 WebSocket 握手路径（与 application.yml 中 WS 路由一致）。
     *
     * <p>{@code /im/world} 是 3D 蓝鲸城的世界频道端点，与 {@code /im/ws} 共用
     * {@code WsTicketService} 的一次性 ticket；不在这里登记的话，缺 ticket 的客户端
     * 会先被协议升级、再被下游拒绝，中间那一瞬的 101 会让前端误判"已连上"。</p>
     */
    private static final Set<String> WS_PATHS = Set.of("/im/ws", "/im/world", "/doc/ws");

    /** 一次性凭证查询参数名 */
    private static final String TICKET_PARAM = "ticket";

    /** 过滤器顺序：晚于 JWT 鉴权（-100），早于协议升级 */
    private static final int ORDER = -95;

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
        if (!WS_PATHS.contains(request.getURI().getPath())) {
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
     * 写出 401 未认证响应（与业务侧 Result 同结构），并透传链路追踪头。
     *
     * @param exchange 当前请求上下文
     * @return 响应写出完成的信号
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
        return ORDER;
    }
}
