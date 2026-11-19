package com.bluewhale.gateway.filter;

import com.bluewhale.gateway.common.GatewayHeaders;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 网关语言上下文过滤器测试。
 *
 * @author BlueWhale
 */
class LocaleGlobalFilterTest {

    private final LocaleGlobalFilter filter = new LocaleGlobalFilter();

    @Test
    @DisplayName("Accept-Language 归一化为 X-Lang 并覆盖下游头")
    void shouldNormalizeAcceptLanguage() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/auth/v1/me")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-GB,en;q=0.9")
                        .build());
        AtomicReference<ServerWebExchange> seen = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            seen.set(ex);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertEquals("en-US", seen.get().getRequest().getHeaders().getFirst(GatewayHeaders.LANGUAGE));
        assertEquals("en-US", seen.get().getRequest().getHeaders().getFirst(HttpHeaders.ACCEPT_LANGUAGE));
        assertEquals("en-US", exchange.getResponse().getHeaders().getFirst(HttpHeaders.CONTENT_LANGUAGE));
    }

    @Test
    @DisplayName("显式 X-Lang 优先，缺省回落 zh-CN")
    void shouldPreferExplicitLanguageAndDefault() {
        ServerWebExchange explicit = capture(MockServerWebExchange.from(
                MockServerHttpRequest.get("/auth/v1/me")
                        .header(GatewayHeaders.LANGUAGE, "zh_CN")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .build()));
        assertEquals("zh-CN", explicit.getRequest().getHeaders().getFirst(GatewayHeaders.LANGUAGE));

        ServerWebExchange fallback = capture(MockServerWebExchange.from(
                MockServerHttpRequest.get("/auth/v1/me").build()));
        assertEquals("zh-CN", fallback.getRequest().getHeaders().getFirst(GatewayHeaders.LANGUAGE));
    }

    @Test
    @DisplayName("过滤器顺序早于追踪与鉴权")
    void shouldRunBeforeAuthFilters() {
        assertEquals(-300, filter.getOrder());
    }

    /**
     * 执行过滤器并返回向下游透传的请求。
     *
     * @param exchange 原始请求
     * @return 下游请求
     */
    private ServerWebExchange capture(MockServerWebExchange exchange) {
        AtomicReference<ServerWebExchange> seen = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            seen.set(ex);
            return Mono.empty();
        };
        filter.filter(exchange, chain).block();
        return seen.get();
    }
}
