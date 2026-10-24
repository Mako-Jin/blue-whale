package com.bluewhale.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link ImWsTicketGuardGlobalFilter} 单元测试（T19）：/im/ws 缺 ticket 拒绝升级，携带 ticket 与其他路径放行。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class ImWsTicketGuardGlobalFilterTest {

    private static final String EXPECTED_401_BODY = "{\"code\":401,\"message\":\"未认证\",\"data\":null}";

    @Mock
    private GatewayFilterChain chain;

    private ImWsTicketGuardGlobalFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ImWsTicketGuardGlobalFilter();
    }

    /**
     * 构造指定路径的模拟请求上下文
     *
     * @param url 请求 URL
     * @return 模拟请求上下文
     */
    private static MockServerWebExchange buildExchange(String url) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(url).build());
    }

    @Test
    @DisplayName("filter：/im/ws 无 ticket 时返回 401 且不进入协议升级")
    void shouldRejectWhenTicketMissing() {
        MockServerWebExchange exchange = buildExchange("http://localhost/im/ws");

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertEquals(EXPECTED_401_BODY, exchange.getResponse().getBodyAsString().block());
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("filter：/im/ws 的 ticket 为空白时同样拒绝")
    void shouldRejectWhenTicketBlank() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/im/ws").queryParam("ticket", "   ").build());

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("filter：/im/ws 携带 ticket 时放行至协议升级")
    void shouldPassThroughWhenTicketPresent() {
        MockServerWebExchange exchange = buildExchange("http://localhost/im/ws?ticket=abcdef0123456789");
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("filter：非 /im/ws 路径不受影响")
    void shouldIgnoreOtherPaths() {
        MockServerWebExchange exchange = buildExchange("http://localhost/im/conversations");
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        assertNull(exchange.getResponse().getStatusCode());
    }
}
