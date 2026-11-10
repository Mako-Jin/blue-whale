package com.bluewhale.common.internaltoken;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link InternalTokenFilter} 单元测试（V-02）：令牌缺失/错误拒绝、合法放行、
 * 非内网路径与豁免路径放行、未配置令牌 fail-open、开关关闭放行。
 *
 * @author BlueWhale
 */
class InternalTokenFilterTest {

    private InternalTokenProperties properties;

    private InternalTokenFilter filter;

    @BeforeEach
    void setUp() {
        properties = new InternalTokenProperties();
        properties.setToken("secret-token");
        filter = new InternalTokenFilter(properties);
    }

    /**
     * 执行过滤并返回响应
     *
     * @param path   请求路径
     * @param header 令牌头（null 表示不携带）
     * @return 响应
     * @throws ServletException 过滤器异常
     * @throws IOException      响应写出异常
     */
    private MockHttpServletResponse doFilter(String path, String header) throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRequestURI(path);
        if (header != null) {
            request.addHeader(properties.getHeader(), header);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    @DisplayName("filter：内网路径缺失令牌头返回 403 JSON")
    void shouldDenyWhenTokenHeaderMissing() throws Exception {
        MockHttpServletResponse response = doFilter("/file/internal/1", null);

        assertEquals(403, response.getStatus());
        assertEquals("application/json;charset=UTF-8", response.getContentType());
        assertEquals("{\"code\":403,\"message\":\"内网接口令牌缺失或不正确\",\"data\":null}",
                response.getContentAsString());
    }

    @Test
    @DisplayName("filter：内网路径令牌错误返回 403")
    void shouldDenyWhenTokenWrong() throws Exception {
        MockHttpServletResponse response = doFilter("/file/internal/1", "wrong-token");

        assertEquals(403, response.getStatus());
    }

    @Test
    @DisplayName("filter：内网路径携带合法令牌放行")
    void shouldAllowWhenTokenValid() throws Exception {
        MockHttpServletResponse response = doFilter("/file/internal/1", "secret-token");

        assertEquals(200, response.getStatus());
    }

    @Test
    @DisplayName("filter：任意服务前缀的 internal 段路径均受保护（/**/internal/**）")
    void shouldProtectAnyPathWithInternalSegment() throws Exception {
        assertEquals(403, doFilter("/im/internal/notify", null).getStatus());
        assertEquals(403, doFilter("/workflow/internal/task/1/complete", "bad").getStatus());
    }

    @Test
    @DisplayName("filter：internal 段前超过一段的路径仍受保护（令牌层是唯一防线）")
    void shouldProtectDeepInternalSegment() throws Exception {
        // 网关 internal-deny 只配了 /*/internal/**（星号不跨段），
        // /twin/notify/internal/replay 这类深路径网关拦不住，只能靠本令牌层；
        // 因此新增内网端点若用了多段命名，令牌层必须继续覆盖，否则等于对外裸奔。
        assertEquals(403, doFilter("/twin/notify/internal/replay", null).getStatus());
        assertEquals(200, doFilter("/twin/notify/internal/replay", "secret-token").getStatus());
    }

    @Test
    @DisplayName("filter：非内网路径放行")
    void shouldAllowPublicPath() throws Exception {
        assertEquals(200, doFilter("/file/public/1", null).getStatus());
    }

    @Test
    @DisplayName("filter：前缀相近但段不匹配的路径放行（/file/internals/1）")
    void shouldAllowPathThatOnlySharesPrefixText() throws Exception {
        assertEquals(200, doFilter("/file/internals/1", null).getStatus());
    }

    @Test
    @DisplayName("filter：豁免路径（Alertmanager 入站）不校验令牌放行")
    void shouldAllowExcludedPath() throws Exception {
        properties.setExcludePatterns(List.of("/alert/internal/prometheus/**"));

        assertEquals(200, doFilter("/alert/internal/prometheus", null).getStatus());
    }

    @Test
    @DisplayName("filter：审计摄入端点 /audit/log/ingest/** 受默认模式保护")
    void shouldProtectAuditIngestPath() throws Exception {
        assertEquals(403, doFilter("/audit/log/ingest", null).getStatus());
    }

    @Test
    @DisplayName("filter：令牌未配置时 fail-open 放行")
    void shouldFailOpenWhenTokenBlank() throws Exception {
        properties.setToken("");

        assertEquals(200, doFilter("/file/internal/1", null).getStatus());
    }

    @Test
    @DisplayName("filter：显式配置环境变量令牌时 resolveToken 生效")
    void shouldResolveExplicitTokenOverEnv() {
        properties.setToken("explicit");

        assertEquals("explicit", properties.resolveToken());
    }
}
