package com.bluewhale.gateway.handler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@link GatewayExceptionHandler} 单元测试（P2-10）：404 语义区分与 5xx 文案映射。
 *
 * @author BlueWhale
 */
class GatewayExceptionHandlerTest {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String PATH = "http://localhost/auth/v1/me";

    private final GatewayExceptionHandler handler = new GatewayExceptionHandler();

    /**
     * 构造模拟请求上下文
     *
     * @return 模拟请求上下文
     */
    private static MockServerWebExchange buildExchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get(PATH).build());
    }

    @Test
    @DisplayName("handle：网关未匹配到路由 → 404「路由不存在」")
    void shouldReturnRouteNotFoundMessage() {
        MockServerWebExchange exchange = buildExchange();

        handler.handle(exchange, new NotFoundException("Unable to find instance")).block();

        assertEquals(HttpStatus.NOT_FOUND, exchange.getResponse().getStatusCode());
        assertEquals("{\"code\":404,\"message\":\"路由不存在\",\"data\":null}",
                exchange.getResponse().getBodyAsString().block());
    }

    @Test
    @DisplayName("handle：Accept-Language en-US → 英文路由不存在文案")
    void shouldLocalizeRouteNotFoundMessage() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(PATH).header(HttpHeaders.ACCEPT_LANGUAGE, "en-US").build());

        handler.handle(exchange, new NotFoundException("Unable to find instance")).block();

        assertEquals("{\"code\":404,\"message\":\"Route not found\",\"data\":null}",
                exchange.getResponse().getBodyAsString().block());
    }

    @Test
    @DisplayName("handle：下游返回 404 → 404「下游资源不存在」")
    void shouldReturnDownstreamNotFoundMessage() {
        MockServerWebExchange exchange = buildExchange();

        handler.handle(exchange, new ResponseStatusException(HttpStatus.NOT_FOUND, "downstream 404")).block();

        assertEquals(HttpStatus.NOT_FOUND, exchange.getResponse().getStatusCode());
        assertEquals("{\"code\":404,\"message\":\"下游资源不存在\",\"data\":null}",
                exchange.getResponse().getBodyAsString().block());
    }

    @Test
    @DisplayName("handle：下游 503 保留状态码并生成 X-Trace-Id")
    void shouldKeepServiceUnavailableAndGenerateTraceId() {
        MockServerWebExchange exchange = buildExchange();

        handler.handle(exchange, new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "down")).block();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.getResponse().getStatusCode());
        assertEquals("{\"code\":503,\"message\":\"服务暂时不可用\",\"data\":null}",
                exchange.getResponse().getBodyAsString().block());
        assertNotNull(exchange.getResponse().getHeaders().getFirst(TRACE_ID_HEADER));
    }

    @Test
    @DisplayName("handle：其他异常 → 500「系统异常」")
    void shouldReturnServerErrorMessage() {
        MockServerWebExchange exchange = buildExchange();

        handler.handle(exchange, new IllegalStateException("boom")).block();

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exchange.getResponse().getStatusCode());
        assertEquals("{\"code\":500,\"message\":\"系统异常\",\"data\":null}",
                exchange.getResponse().getBodyAsString().block());
    }
}
