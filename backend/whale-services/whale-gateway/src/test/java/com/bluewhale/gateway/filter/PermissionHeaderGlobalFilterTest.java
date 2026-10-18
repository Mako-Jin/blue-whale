package com.bluewhale.gateway.filter;

import com.bluewhale.gateway.config.PermissionHeaderProperties;
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
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 权限码头注入全局过滤器单测（AUTH-06 / checklist 8.10）。
 *
 * <p>覆盖：开关关闭/缺 {@code X-User-Id} 直放行不注入、Redis 命中即注入并移除伪造同名头、
 * 命中哨兵 {@code __NULL__} 或未命中回落 {@code /auth/me}、两级均失败注入空串不阻断、order=-50。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class PermissionHeaderGlobalFilterTest {

    /** 网关鉴权后注入的用户ID头 */
    private static final String USER_ID_HEADER = "X-User-Id";
    /** 权限码头名 */
    private static final String CODES_HEADER = "X-Permission-Codes";
    /** 空值哨兵（命中视为未命中） */
    private static final String NULL_SENTINEL = "__NULL__";
    /** 用户ID */
    private static final String USER_ID = "1001";
    /** 权限码 */
    private static final String CODE = "sys:audit:log:list";
    /** 受保护请求地址 */
    private static final String REQUEST_URL = "http://localhost/audit/log/frontend/page";

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;
    @Mock
    private ReactiveValueOperations<String, String> valueOperations;
    @Mock
    private GatewayFilterChain chain;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private PermissionHeaderProperties properties;
    private PermissionHeaderGlobalFilter filter;

    @BeforeEach
    void setUp() {
        properties = new PermissionHeaderProperties();
        filter = new PermissionHeaderGlobalFilter(properties, redisTemplate, WebClient.builder(), objectMapper);
    }

    @Test
    @DisplayName("开关关闭：直接放行不访问 Redis 也不注入")
    void shouldPassThroughWhenDisabled() {
        properties.setEnabled(false);
        MockServerWebExchange exchange = exchangeWithUser();
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        assertThat(capturedCodes()).isNull();
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("缺 X-User-Id：直接放行不注入")
    void shouldPassThroughWhenUserIdMissing() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(REQUEST_URL).build());
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        assertThat(capturedCodes()).isNull();
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("Redis 命中：注入权限码并移除伪造同名头")
    void shouldInjectCodesWhenRedisHits() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(REQUEST_URL)
                .header(USER_ID_HEADER, USER_ID)
                .header(CODES_HEADER, "forged-by-client")
                .build());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(Mono.just("a," + CODE));
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        assertThat(capturedCodes()).isEqualTo("a," + CODE);
    }

    @Test
    @DisplayName("Redis 命中哨兵：回落 /auth/me 取 data.permissions 注入")
    void shouldFallbackWhenRedisHitsNullSentinel() {
        MockServerWebExchange exchange = exchangeWithUser();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(Mono.just(NULL_SENTINEL));
        stubAuthMe("{\"data\":{\"permissions\":[\"" + CODE + "\"]}}");
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        assertThat(capturedCodes()).isEqualTo(CODE);
    }

    @Test
    @DisplayName("Redis 未命中：回落 /auth/me")
    void shouldFallbackWhenRedisMisses() {
        MockServerWebExchange exchange = exchangeWithUser();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(Mono.empty());
        stubAuthMe("{\"data\":{\"permissions\":[\"a\",\"" + CODE + "\"]}}");
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        assertThat(capturedCodes()).isEqualTo("a," + CODE);
    }

    @Test
    @DisplayName("Redis 与回落两级均失败：注入空串且不阻断请求")
    void shouldInjectEmptyCodesWhenBothLevelsFail() {
        MockServerWebExchange exchange = exchangeWithUser();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(Mono.error(new IllegalStateException("redis down")));
        stubAuthMeError();
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        assertThat(capturedCodes()).isEmpty();
    }

    @Test
    @DisplayName("order：固定为 -50")
    void shouldHaveFixedOrder() {
        assertThat(filter.getOrder()).isEqualTo(-50);
    }

    /**
     * 构造带 X-User-Id 的请求上下文
     *
     * @return 模拟请求上下文
     */
    private static MockServerWebExchange exchangeWithUser() {
        return MockServerWebExchange.from(MockServerHttpRequest.get(REQUEST_URL)
                .header(USER_ID_HEADER, USER_ID)
                .build());
    }

    /**
     * 捕获注入后传给过滤器链的请求上下文，并读取权限码头
     *
     * @return 注入的权限码；未注入时返回 {@code null}
     */
    private String capturedCodes() {
        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        return captor.getValue().getRequest().getHeaders().getFirst(CODES_HEADER);
    }

    /**
     * 桩定回落 {@code /auth/me} 返回指定响应体
     *
     * @param body 响应体 JSON 文本
     */
    private void stubAuthMe(String body) {
        stubAuthMeResponse(Mono.just(body));
    }

    /**
     * 桩定回落 {@code /auth/me} 调用失败
     */
    private void stubAuthMeError() {
        stubAuthMeResponse(Mono.error(new IllegalStateException("auth down")));
    }

    /**
     * 用桩定的 WebClient.Builder 重建过滤器，并桩定回落 {@code /auth/me} 的响应信号。
     *
     * <p>WebClient 链使用原始类型（raw type）Mock 以规避 {@code get()} 返回泛型通配符 {@code ?} 的编译限制。</p>
     *
     * @param response {@code bodyToMono} 的响应信号（成功值或错误）
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubAuthMeResponse(Mono<String> response) {
        WebClient webClient = mock(WebClient.class);
        WebClient.RequestHeadersUriSpec uriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);
        WebClient.Builder builder = mock(WebClient.Builder.class);
        filter = new PermissionHeaderGlobalFilter(properties, redisTemplate, builder, objectMapper);
        when(builder.build()).thenReturn(webClient);
        when(webClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(headersSpec);
        when(headersSpec.headers(any())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(response);
    }
}
