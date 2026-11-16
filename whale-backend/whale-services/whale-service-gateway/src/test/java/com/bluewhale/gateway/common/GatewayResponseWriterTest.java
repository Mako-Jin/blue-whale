package com.bluewhale.gateway.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link GatewayResponseWriter} 单元测试（P2-1）：状态码/响应体/traceId 透传与已提交保护。
 *
 * @author BlueWhale
 */
class GatewayResponseWriterTest {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Test
    @DisplayName("write：写出状态码、JSON 体与 Content-Type")
    void shouldWriteStatusAndBody() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/auth/v1/me").build());

        GatewayResponseWriter.write(exchange, HttpStatus.UNAUTHORIZED, 401, "未认证").block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertEquals("{\"code\":401,\"message\":\"未认证\",\"data\":null}",
                exchange.getResponse().getBodyAsString().block());
        assertEquals("application/json;charset=UTF-8",
                exchange.getResponse().getHeaders().getContentType().toString());
    }

    @Test
    @DisplayName("write：透传入站 X-Trace-Id，缺失时不生成")
    void shouldPassThroughInboundTraceId() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/auth/v1/me")
                        .header(TRACE_ID_HEADER, "trace-passthrough")
                        .build());

        GatewayResponseWriter.write(exchange, HttpStatus.TOO_MANY_REQUESTS, 429, "限流").block();

        assertEquals("trace-passthrough", exchange.getResponse().getHeaders().getFirst(TRACE_ID_HEADER));

        MockServerWebExchange noTrace = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/auth/v1/me").build());
        GatewayResponseWriter.write(noTrace, HttpStatus.FORBIDDEN, 403, "禁止访问").block();

        assertNull(noTrace.getResponse().getHeaders().getFirst(TRACE_ID_HEADER));
    }

    @Test
    @DisplayName("write：入站缺失且要求生成时补齐 X-Trace-Id")
    void shouldGenerateTraceIdWhenRequested() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/auth/v1/me").build());

        GatewayResponseWriter.write(exchange, HttpStatus.SERVICE_UNAVAILABLE, 503, "服务暂时不可用", true).block();

        String traceId = exchange.getResponse().getHeaders().getFirst(TRACE_ID_HEADER);
        assertNotNull(traceId);
        assertEquals(32, traceId.length());
    }

    @Test
    @DisplayName("write：响应已提交时不重复写出")
    void shouldSkipWhenCommitted() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/auth/v1/me").build());
        exchange.getResponse().setComplete().block();

        assertDoesNotThrow(() -> GatewayResponseWriter.write(exchange, HttpStatus.BAD_GATEWAY, 502, "错误").block());
        assertEquals("", exchange.getResponse().getBodyAsString().block());
    }
}
