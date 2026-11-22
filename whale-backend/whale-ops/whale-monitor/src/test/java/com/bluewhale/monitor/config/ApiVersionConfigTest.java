package com.bluewhale.monitor.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.accept.ApiVersionResolver;
import org.springframework.web.util.ServletRequestPathUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ApiVersionConfig} 路径版本解析器测试：只认 {@code /monitor/v{n}/...}，其余保持无版本。
 *
 * @author BlueWhale
 */
class ApiVersionConfigTest {

    private static MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(uri);
        // PathApiVersionResolver 从已解析的请求路径里取第 2 段；Web 链路上由过滤器注入，单测里手工补上
        ServletRequestPathUtils.parseAndCache(request);
        return request;
    }

    @Test
    @DisplayName("版本化路径：解析出第 2 段的 v{n}")
    void shouldResolveVersionFromSecondSegment() {
        ApiVersionResolver resolver = new ApiVersionConfig().pathApiVersionResolver();

        assertThat(resolver.resolveVersion(request("/monitor/v1/orders"))).isEqualTo("v1");
        assertThat(resolver.resolveVersion(request("/monitor/v23/halls"))).isEqualTo("v23");
    }

    @Test
    @DisplayName("非版本路径：概览等端点保持无版本")
    void shouldIgnoreNonVersionedPath() {
        ApiVersionResolver resolver = new ApiVersionConfig().pathApiVersionResolver();

        assertThat(resolver.resolveVersion(request("/monitor/overview"))).isNull();
        assertThat(resolver.resolveVersion(request("/monitor/overview/halls"))).isNull();
    }
}
