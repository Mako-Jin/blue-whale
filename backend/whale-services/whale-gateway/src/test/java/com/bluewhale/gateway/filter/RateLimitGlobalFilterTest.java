package com.bluewhale.gateway.filter;

import com.bluewhale.gateway.config.RateLimitProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link RateLimitGlobalFilter} 单元测试：阈值内放行、超阈值 429 短路、Redis 异常降级放行、开关关闭放行（T5.4 / AUTH-30），
 * 以及按路径特化规则命中/最长匹配/未命中回落全局（REQ-SEC-006）。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class RateLimitGlobalFilterTest {

    private static final String REQUEST_URL = "http://localhost/auth/login";
    private static final String REPORT_URL = "http://localhost/audit/log/frontend/batch";
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String CLIENT_IP = "203.0.113.7";
    private static final String REPORT_RULE_PATTERN = "/audit/log/frontend/**";
    private static final String BROAD_RULE_PATTERN = "/audit/**";
    private static final int WINDOW_SECONDS = 60;
    private static final int REPORT_RULE_LIMIT = 1;
    private static final int BROAD_RULE_LIMIT = 10;
    private static final long COUNT_WITHIN_LIMIT = 5L;
    private static final long COUNT_OVER_LIMIT = 201L;
    private static final long COUNT_WITHIN_REPORT_RULE = 1L;
    private static final long COUNT_OVER_REPORT_RULE = 2L;
    private static final String EXPECTED_429_BODY =
            "{\"code\":429,\"message\":\"请求过于频繁，请稍后重试\",\"data\":null}";

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    @Mock
    private GatewayFilterChain chain;

    private RateLimitProperties properties;

    private RateLimitGlobalFilter filter;

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties();
        filter = new RateLimitGlobalFilter(redisTemplate, properties);
    }

    /**
     * 构造携带 X-Forwarded-For 的模拟请求上下文
     *
     * @param url 请求 URL
     * @return 模拟请求上下文
     */
    private static MockServerWebExchange buildExchange(String url) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(url)
                .header(X_FORWARDED_FOR, CLIENT_IP)
                .build());
    }

    /**
     * 构造携带 X-Forwarded-For 的默认模拟请求上下文（{@code /auth/login}）
     *
     * @return 模拟请求上下文
     */
    private static MockServerWebExchange buildExchange() {
        return buildExchange(REQUEST_URL);
    }

    /**
     * 构造路径限流规则
     *
     * @param pathPattern Ant 风格路径模式
     * @param limit 窗口内最大请求数
     * @return 路径限流规则
     */
    private static RateLimitProperties.PathRule pathRule(String pathPattern, int limit) {
        RateLimitProperties.PathRule rule = new RateLimitProperties.PathRule();
        rule.setPathPattern(pathPattern);
        rule.setLimit(limit);
        rule.setWindowSeconds(WINDOW_SECONDS);
        return rule;
    }

    @Test
    @DisplayName("filter：计数未超阈值时放行并继续过滤器链，键前缀与客户端标识正确")
    void shouldPassThroughWhenCountWithinLimit() {
        MockServerWebExchange exchange = buildExchange();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(Mono.just(COUNT_WITHIN_LIMIT));
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        assertNull(exchange.getResponse().getStatusCode());
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).increment(keyCaptor.capture());
        assertTrue(keyCaptor.getValue().startsWith("whale:gateway:rate-limit:"),
                "限流键须以配置前缀开头");
        assertTrue(keyCaptor.getValue().endsWith(":" + CLIENT_IP),
                "限流键末段须为客户端标识");
    }

    @Test
    @DisplayName("filter：计数超阈值时返回 429 并写出 Result 体，且不继续过滤器链")
    void shouldReturnTooManyRequestsWhenCountOverLimit() {
        MockServerWebExchange exchange = buildExchange();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(Mono.just(COUNT_OVER_LIMIT));

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
        assertEquals(EXPECTED_429_BODY, exchange.getResponse().getBodyAsString().block());
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("filter：Redis 抛异常时降级放行，不阻断请求")
    void shouldPassThroughWhenRedisUnavailable() {
        MockServerWebExchange exchange = buildExchange();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(Mono.error(new RuntimeException("redis down")));
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("filter：限流开关关闭时直接放行，不访问 Redis")
    void shouldPassThroughWhenDisabled() {
        MockServerWebExchange exchange = buildExchange();
        properties.setEnabled(false);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("filter：上报路径命中特化规则，超规则阈值返回 429，键含规则标识")
    void shouldApplySpecializedRuleWhenReportPathMatched() {
        properties.setPathRules(List.of(pathRule(REPORT_RULE_PATTERN, REPORT_RULE_LIMIT)));
        MockServerWebExchange exchange = buildExchange(REPORT_URL);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(Mono.just(COUNT_OVER_REPORT_RULE));

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
        verifyNoInteractions(chain);
        assertTrue(captureKey().contains(":" + REPORT_RULE_PATTERN + ":"), "限流键须含命中规则标识");
    }

    @Test
    @DisplayName("filter：上报路径命中特化规则但未超规则阈值时放行")
    void shouldPassThroughWhenWithinSpecializedRuleLimit() {
        properties.setPathRules(List.of(pathRule(REPORT_RULE_PATTERN, REPORT_RULE_LIMIT)));
        MockServerWebExchange exchange = buildExchange(REPORT_URL);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(Mono.just(COUNT_WITHIN_REPORT_RULE));
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(Boolean.TRUE));
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("filter：多规则命中时取最长 pathPattern（特化优先）")
    void shouldPickLongestMatchingRule() {
        properties.setPathRules(List.of(
                pathRule(BROAD_RULE_PATTERN, BROAD_RULE_LIMIT),
                pathRule(REPORT_RULE_PATTERN, REPORT_RULE_LIMIT)));
        MockServerWebExchange exchange = buildExchange(REPORT_URL);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(Mono.just(COUNT_OVER_REPORT_RULE));

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode(),
                "若误取宽泛规则（阈值 10），计数 2 将放行");
        assertTrue(captureKey().contains(":" + REPORT_RULE_PATTERN + ":"), "须取最长匹配规则标识");
    }

    @Test
    @DisplayName("filter：未命中任何路径规则时沿用全局阈值，键规则标识为 default")
    void shouldFallBackToGlobalLimitWhenNoRuleMatched() {
        properties.setPathRules(List.of(pathRule(REPORT_RULE_PATTERN, REPORT_RULE_LIMIT)));
        MockServerWebExchange exchange = buildExchange();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(Mono.just(COUNT_OVER_LIMIT));

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
        assertTrue(captureKey().contains(":default:"), "未命中规则须使用 default 规则标识");
    }

    /**
     * 捕获本次请求实际使用的 Redis 限流计数键
     *
     * @return 限流计数键
     */
    private String captureKey() {
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).increment(keyCaptor.capture());
        return keyCaptor.getValue();
    }
}
