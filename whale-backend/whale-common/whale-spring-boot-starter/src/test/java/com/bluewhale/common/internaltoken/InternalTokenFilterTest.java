package com.bluewhale.common.internaltoken;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        return doFilter(path, header, null);
    }

    /**
     * 执行过滤并返回响应（可携带租户头）
     *
     * @param path     请求路径
     * @param header   令牌头（null 表示不携带）
     * @param tenantId 租户头（null 表示不携带）
     * @return 响应
     * @throws ServletException 过滤器异常
     * @throws IOException      响应写出异常
     */
    private MockHttpServletResponse doFilter(String path, String header, String tenantId)
            throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRequestURI(path);
        if (header != null) {
            request.addHeader(properties.getHeader(), header);
        }
        if (tenantId != null) {
            request.addHeader(properties.getTenantHeader(), tenantId);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    /**
     * 启用按服务签发模式：mall 仅允许租户 1001/1002，job 为平台级服务
     */
    private void enableIssuerMode() {
        InternalTokenProperties.Issuer mall = new InternalTokenProperties.Issuer();
        mall.setSecret("mall-secret");
        mall.setTenants(List.of("1001", "1002"));
        InternalTokenProperties.Issuer job = new InternalTokenProperties.Issuer();
        job.setSecret("job-secret");
        job.setPlatform(true);
        Map<String, InternalTokenProperties.Issuer> issuers = new LinkedHashMap<>();
        issuers.put("mall", mall);
        issuers.put("job", job);
        properties.setIssuers(issuers);
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
        assertEquals(403, doFilter("/workflow/v1/internal/task/1/complete", "bad").getStatus());
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
        properties.setExcludePatterns(List.of("/alert/v1/internal/prometheus/**"));

        assertEquals(200, doFilter("/alert/v1/internal/prometheus", null).getStatus());
    }

    @Test
    @DisplayName("filter：审计摄入端点 /audit/v1/log/ingest/** 受默认模式保护")
    void shouldProtectAuditIngestPath() throws Exception {
        assertEquals(403, doFilter("/audit/v1/log/ingest", null).getStatus());
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

    @Test
    @DisplayName("filter：按服务令牌 + 白名单内租户放行")
    void shouldAllowIssuerWithAllowedTenant() throws Exception {
        enableIssuerMode();

        assertEquals(200, doFilter("/pay/internal/order/O1", "mall.mall-secret", "1001").getStatus());
    }

    @Test
    @DisplayName("filter：按服务令牌 + 白名单外租户返回 403")
    void shouldDenyIssuerWithForeignTenant() throws Exception {
        enableIssuerMode();

        MockHttpServletResponse response = doFilter("/pay/internal/order/O1", "mall.mall-secret", "2002");

        assertEquals(403, response.getStatus());
        assertEquals("{\"code\":403,\"message\":\"内网令牌无权访问该租户\",\"data\":null}",
                response.getContentAsString());
    }

    @Test
    @DisplayName("filter：非平台服务缺少租户头返回 403")
    void shouldDenyIssuerWithoutTenant() throws Exception {
        enableIssuerMode();

        MockHttpServletResponse response = doFilter("/pay/internal/order/O1", "mall.mall-secret", null);

        assertEquals(403, response.getStatus());
        assertEquals("{\"code\":403,\"message\":\"内网接口缺少租户头\",\"data\":null}",
                response.getContentAsString());
    }

    @Test
    @DisplayName("filter：平台级服务不校验租户头")
    void shouldAllowPlatformIssuerWithoutTenant() throws Exception {
        enableIssuerMode();

        assertEquals(200, doFilter("/pay/internal/order/O1", "job.job-secret", null).getStatus());
    }

    @Test
    @DisplayName("filter：服务密钥错误、未知服务、旧共享令牌均返回 403")
    void shouldDenyInvalidIssuerTokens() throws Exception {
        enableIssuerMode();

        assertEquals(403, doFilter("/pay/internal/order/O1", "mall.wrong", "1001").getStatus());
        assertEquals(403, doFilter("/pay/internal/order/O1", "unknown.secret", "1001").getStatus());
        // 按服务模式开启后共享令牌不再被接受
        assertEquals(403, doFilter("/pay/internal/order/O1", "secret-token", "1001").getStatus());
        // 缺少分隔符的裸令牌
        assertEquals(403, doFilter("/pay/internal/order/O1", "mall-secret", "1001").getStatus());
    }
}
