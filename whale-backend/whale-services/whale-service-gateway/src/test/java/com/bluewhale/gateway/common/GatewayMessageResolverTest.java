package com.bluewhale.gateway.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link GatewayMessageResolver} 单元测试：默认中文、英文协商、{@code X-Lang} 优先与缺失键兜底。
 *
 * @author BlueWhale
 */
class GatewayMessageResolverTest {

    private static final String PATH = "http://localhost/auth/v1/me";

    @Test
    @DisplayName("resolve：无语言头 → 默认中文")
    void shouldReturnChineseByDefault() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(PATH).build());

        assertEquals("未认证", GatewayMessageResolver.resolve(exchange, "gateway.auth.unauthorized", "兜底"));
    }

    @Test
    @DisplayName("resolve：Accept-Language en-US → 英文文案")
    void shouldResolveEnglishByAcceptLanguage() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(PATH).header(HttpHeaders.ACCEPT_LANGUAGE, "en-US").build());

        assertEquals("Unauthorized",
                GatewayMessageResolver.resolve(exchange, "gateway.auth.unauthorized", "兜底"));
    }

    @Test
    @DisplayName("resolve：X-Lang 优先于 Accept-Language")
    void shouldPreferExplicitLanguageHeader() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(PATH)
                        .header(GatewayHeaders.LANGUAGE, "zh-CN")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .build());

        assertEquals("未认证", GatewayMessageResolver.resolve(exchange, "gateway.auth.unauthorized", "兜底"));
    }

    @Test
    @DisplayName("resolve：键不存在 → 返回调用方中文兜底")
    void shouldFallbackWhenKeyMissing() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(PATH).header(HttpHeaders.ACCEPT_LANGUAGE, "en-US").build());

        assertEquals("兜底", GatewayMessageResolver.resolve(exchange, "gateway.missing.key", "兜底"));
    }

    @Test
    @DisplayName("resolveLocale：语言头非法 → 回落 zh-CN")
    void shouldNormalizeUnsupportedLanguage() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(PATH).header(HttpHeaders.ACCEPT_LANGUAGE, "ja-JP").build());

        assertEquals("zh", GatewayMessageResolver.resolveLocale(exchange).getLanguage());
    }
}
