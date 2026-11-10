package com.bluewhale.gateway.filter;

import com.bluewhale.gateway.config.InternalApiDenyProperties;
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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link InternalApiDenyGlobalFilter} 单元测试（T6）：命中内网路径返回 403、非命中放行、开关关闭放行。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class InternalApiDenyGlobalFilterTest {

    private static final String INTERNAL_PATH = "http://localhost/file/internal/upload";
    private static final String PUBLIC_PATH = "http://localhost/file/public/1";
    private static final String EXPECTED_403_BODY =
            "{\"code\":403,\"message\":\"内网接口不允许外部访问\",\"data\":null}";

    @Mock
    private GatewayFilterChain chain;

    private InternalApiDenyProperties properties;

    private InternalApiDenyGlobalFilter filter;

    @BeforeEach
    void setUp() {
        properties = new InternalApiDenyProperties();
        filter = new InternalApiDenyGlobalFilter(properties);
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
    @DisplayName("filter：命中 /file/internal/** 时返回 403 且不进入下游过滤器链")
    void shouldDenyWhenInternalPathMatched() {
        MockServerWebExchange exchange = buildExchange(INTERNAL_PATH);

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        assertEquals(EXPECTED_403_BODY, exchange.getResponse().getBodyAsString().block());
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("filter：非内网路径放行并继续过滤器链")
    void shouldPassThroughWhenPathNotMatched() {
        MockServerWebExchange exchange = buildExchange(PUBLIC_PATH);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("filter：开关关闭时内网路径也放行")
    void shouldPassThroughWhenDisabled() {
        properties.setEnabled(false);
        MockServerWebExchange exchange = buildExchange(INTERNAL_PATH);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("GW-06 filter：配置多个模式时，任一模式命中的路径均拒绝")
    void shouldDenyWhenAnyOfMultiplePatternsMatched() {
        properties.setPatterns(new ArrayList<>(List.of("/job/internal/**", "/audit/internal/**")));
        MockServerWebExchange jobExchange = buildExchange("http://localhost/job/internal/run");
        MockServerWebExchange auditExchange = buildExchange("http://localhost/audit/internal/dump");

        filter.filter(jobExchange, chain).block();
        filter.filter(auditExchange, chain).block();

        assertEquals(HttpStatus.FORBIDDEN, jobExchange.getResponse().getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, auditExchange.getResponse().getStatusCode());
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("GW-06 filter：/file/internal/** 通配匹配多层级深层路径")
    void shouldDenyDeepNestedPathUnderDoubleStarWildcard() {
        MockServerWebExchange exchange = buildExchange("http://localhost/file/internal/a/b/c/upload");

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("GW-06 filter：/file/internal/** 的 ** 可匹配零层级，即前缀本身也被拒绝")
    void shouldDenyBasePathOfDoubleStarPattern() {
        MockServerWebExchange exchange = buildExchange("http://localhost/file/internal");

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("GW-06 filter：仅前缀相近但段不匹配的路径（/file/internals/1）放行")
    void shouldAllowPathThatOnlySharesPrefixText() {
        MockServerWebExchange exchange = buildExchange("http://localhost/file/internals/1");
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("GW-06 filter：多模式配置下非命中路径仍放行")
    void shouldAllowNonMatchingPathWithMultiplePatternsConfigured() {
        properties.setPatterns(new ArrayList<>(List.of("/file/internal/**", "/job/internal/**")));
        MockServerWebExchange exchange = buildExchange(PUBLIC_PATH);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("V-01 filter：discovery.locator 服务名前缀形态 /whale-file/file/internal/** 同样拒绝")
    void shouldDenyServiceNamePrefixedInternalPath() {
        MockServerWebExchange exchange = buildExchange("http://localhost/whale-file/file/internal/upload");

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("V-01 filter：服务名前缀形态多模式命中（/whale-service-auth/user/internal/**）")
    void shouldDenyServiceNamePrefixedPathForAnyPattern() {
        properties.setPatterns(new ArrayList<>(List.of("/user/internal/**")));
        MockServerWebExchange exchange = buildExchange("http://localhost/whale-service-auth/user/internal/exists");

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("V-01 filter：服务名前缀下的公开路径仍放行")
    void shouldAllowServiceNamePrefixedPublicPath() {
        MockServerWebExchange exchange = buildExchange("http://localhost/whale-file/file/public/1");
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("V-01 filter：服务名前缀下前缀相近但段不匹配的路径仍放行")
    void shouldAllowServiceNamePrefixedPathThatOnlySharesPrefixText() {
        MockServerWebExchange exchange = buildExchange("http://localhost/whale-file/file/internals/1");
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("M0-5 filter：/dept/internal/** 部门树与负责人员路径经网关访问一律 403")
    void shouldDenyDeptInternalPaths() {
        properties.setPatterns(new ArrayList<>(List.of("/dept/internal/**")));
        MockServerWebExchange treeExchange =
                buildExchange("http://localhost/whale-service-auth/dept/internal/tree");
        MockServerWebExchange leaderExchange =
                buildExchange("http://localhost/whale-service-auth/dept/internal/1/leader");

        filter.filter(treeExchange, chain).block();
        filter.filter(leaderExchange, chain).block();

        assertEquals(HttpStatus.FORBIDDEN, treeExchange.getResponse().getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, leaderExchange.getResponse().getStatusCode());
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("V-01 filter：审计摄入端点（无 internal 段）的服务名前缀形态同样拒绝")
    void shouldDenyServiceNamePrefixedAuditIngestPath() {
        properties.setPatterns(new ArrayList<>(List.of("/audit/log/ingest/**")));
        MockServerWebExchange exchange = buildExchange("http://localhost/whale-audit/audit/log/ingest");

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        verifyNoInteractions(chain);
    }
}
