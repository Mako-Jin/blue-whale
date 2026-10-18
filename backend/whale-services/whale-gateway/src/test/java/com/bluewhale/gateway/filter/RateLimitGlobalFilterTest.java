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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link RateLimitGlobalFilter} 单元测试：阈值内放行、超阈值 429 短路、Redis 异常降级放行、开关关闭放行（T5.4 / AUTH-30）。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class RateLimitGlobalFilterTest {

    private static final String REQUEST_URL = "http://localhost/auth/login";
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String CLIENT_IP = "203.0.113.7";
    private static final long COUNT_WITHIN_LIMIT = 5L;
    private static final long COUNT_OVER_LIMIT = 201L;
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
     * @return 模拟请求上下文
     */
    private static MockServerWebExchange buildExchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get(REQUEST_URL)
                .header(X_FORWARDED_FOR, CLIENT_IP)
                .build());
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
}
