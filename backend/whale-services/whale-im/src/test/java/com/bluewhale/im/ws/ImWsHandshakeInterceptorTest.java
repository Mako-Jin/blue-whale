package com.bluewhale.im.ws;

import com.bluewhale.im.service.WsTicketService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ImWsHandshakeInterceptor} 单元测试：ticket 缺失、失效、有效三种握手结果。
 *
 * @author BlueWhale
 */
class ImWsHandshakeInterceptorTest {

    private static final long USER_ID = 10L;

    private final WsTicketService ticketService = mock(WsTicketService.class);

    private final ImWsHandshakeInterceptor interceptor = new ImWsHandshakeInterceptor(ticketService);

    private final WebSocketHandler handler = mock(WebSocketHandler.class);

    private final ServerHttpResponse response = mock(ServerHttpResponse.class);

    @Test
    @DisplayName("有效 ticket：允许升级并把用户ID写入会话属性")
    void validTicketShouldBindUserId() {
        when(ticketService.consume("abc")).thenReturn(USER_ID);
        Map<String, Object> attributes = new HashMap<>();

        boolean allowed = interceptor.beforeHandshake(request("ws://localhost/im/ws?ticket=abc"),
                response, handler, attributes);

        assertTrue(allowed);
        assertEquals(USER_ID, attributes.get(ImWebSocketHandler.ATTR_USER_ID));
    }

    @Test
    @DisplayName("缺少 ticket：拒绝升级并返回 401")
    void missingTicketShouldReject() {
        when(ticketService.consume(null)).thenReturn(null);

        boolean allowed = interceptor.beforeHandshake(request("ws://localhost/im/ws"),
                response, handler, new HashMap<>());

        assertFalse(allowed);
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("ticket 已使用或过期：拒绝升级")
    void usedTicketShouldReject() {
        when(ticketService.consume("used")).thenReturn(null);

        boolean allowed = interceptor.beforeHandshake(request("ws://localhost/im/ws?ticket=used"),
                response, handler, new HashMap<>());

        assertFalse(allowed);
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    /**
     * 构造带指定 URI 的握手请求
     *
     * @param uri 请求地址
     * @return 握手请求桩
     */
    private static ServerHttpRequest request(String uri) {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create(uri));
        return request;
    }
}
