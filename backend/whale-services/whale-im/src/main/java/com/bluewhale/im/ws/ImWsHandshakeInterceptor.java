package com.bluewhale.im.ws;

import com.bluewhale.im.service.WsTicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * WebSocket 握手鉴权拦截器。
 *
 * <p>从查询参数读取一次性 ticket 并原子消费；ticket 无效时返回 401 且中断升级。
 * 认证通过后把用户ID写入会话属性，后续帧处理不再重复校验。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImWsHandshakeInterceptor implements HandshakeInterceptor {

    private final WsTicketService ticketService;

    /**
     * 握手前校验 ticket 并写入用户ID。
     *
     * @param request    握手请求
     * @param response   握手响应
     * @param wsHandler  WebSocket 处理器
     * @param attributes 会话属性
     * @return 是否允许升级
     */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String ticket = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("ticket");
        Long userId = ticketService.consume(ticket);
        if (userId == null) {
            log.warn("WS 握手被拒绝：ticket 缺失、已使用或已过期");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(ImWebSocketHandler.ATTR_USER_ID, userId);
        return true;
    }

    /**
     * 握手后回调（无后置动作）。
     *
     * @param request    握手请求
     * @param response   握手响应
     * @param wsHandler  WebSocket 处理器
     * @param exception  握手异常，成功时为 {@code null}
     */
    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // ticket 已在握手前一次性消费，无后置动作
    }
}
