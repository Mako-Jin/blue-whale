package com.bluewhale.file.filter;

import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.file.config.FileProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FileAccessGuardFilter} 防盗链与限流单元测试。
 *
 * @author BlueWhale
 */
class FileAccessGuardFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private FileProperties properties;

    /**
     * 每个用例使用独立配置，避免开关相互影响
     */
    @BeforeEach
    void setUp() {
        properties = new FileProperties();
    }

    /**
     * 构造过滤器
     *
     * @return 过滤器实例
     */
    private FileAccessGuardFilter filter() {
        return new FileAccessGuardFilter(properties, objectMapper);
    }

    /**
     * 构造下载请求
     *
     * @return 下载请求
     */
    private static MockHttpServletRequest downloadRequest() {
        return new MockHttpServletRequest("GET", "/file/v1/1/download");
    }

    @Test
    @DisplayName("开关关闭时直接放行")
    void shouldPassThroughWhenDisabled() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(downloadRequest(), new MockHttpServletResponse(), chain);

        assertNotNull(chain.getRequest());
    }

    @Test
    @DisplayName("防盗链：非允许来源返回 403 且不进入业务链")
    void shouldRejectForeignReferer() throws Exception {
        properties.getAccess().getHotlink().setEnabled(true);
        properties.getAccess().getHotlink().setAllowedHosts(List.of("example.com"));
        MockHttpServletRequest request = downloadRequest();
        request.addHeader(HttpHeaders.REFERER, "https://evil.com/page");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        assertNull(chain.getRequest());
        assertTrue(response.getContentAsString().contains("403"));
    }

    @Test
    @DisplayName("防盗链：通配后缀与同主机放行，空 Referer 按配置处理")
    void shouldAllowWildcardAndSameHost() throws Exception {
        properties.getAccess().getHotlink().setEnabled(true);
        properties.getAccess().getHotlink().setAllowedHosts(List.of("*.example.com"));

        MockHttpServletRequest wildcard = downloadRequest();
        wildcard.addHeader(HttpHeaders.REFERER, "https://cdn.example.com/page");
        MockFilterChain wildcardChain = new MockFilterChain();
        filter().doFilter(wildcard, new MockHttpServletResponse(), wildcardChain);
        assertNotNull(wildcardChain.getRequest());

        MockHttpServletRequest sameHost = downloadRequest();
        sameHost.addHeader(HttpHeaders.REFERER, "http://localhost/page");
        MockFilterChain sameHostChain = new MockFilterChain();
        filter().doFilter(sameHost, new MockHttpServletResponse(), sameHostChain);
        assertNotNull(sameHostChain.getRequest());

        MockFilterChain emptyChain = new MockFilterChain();
        filter().doFilter(downloadRequest(), new MockHttpServletResponse(), emptyChain);
        assertNotNull(emptyChain.getRequest());

        properties.getAccess().getHotlink().setAllowEmptyReferer(false);
        MockHttpServletResponse rejected = new MockHttpServletResponse();
        MockFilterChain rejectedChain = new MockFilterChain();
        filter().doFilter(downloadRequest(), rejected, rejectedChain);
        assertEquals(403, rejected.getStatus());
        assertNull(rejectedChain.getRequest());
    }

    @Test
    @DisplayName("限流：同一调用者超出桶容量返回 429 并带 Retry-After")
    void shouldRejectWhenRateLimitExceeded() throws Exception {
        properties.getAccess().getRateLimit().setEnabled(true);
        properties.getAccess().getRateLimit().setRequestsPerSecond(1);
        properties.getAccess().getRateLimit().setBurst(1);
        FileAccessGuardFilter filter = filter();

        MockHttpServletRequest first = downloadRequest();
        first.addHeader(WebHeaders.USER_ID, "9");
        MockFilterChain firstChain = new MockFilterChain();
        filter.doFilter(first, new MockHttpServletResponse(), firstChain);
        assertNotNull(firstChain.getRequest());

        MockHttpServletRequest second = downloadRequest();
        second.addHeader(WebHeaders.USER_ID, "9");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain secondChain = new MockFilterChain();
        filter.doFilter(second, response, secondChain);

        assertEquals(429, response.getStatus());
        assertEquals("1", response.getHeader(HttpHeaders.RETRY_AFTER));
        assertNull(secondChain.getRequest());
    }

    @Test
    @DisplayName("非下载/预览路径不拦截")
    void shouldNotFilterOtherPaths() throws Exception {
        properties.getAccess().getHotlink().setEnabled(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/file/v1/1/meta");
        request.addHeader(HttpHeaders.REFERER, "https://evil.com/page");
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(request, new MockHttpServletResponse(), chain);

        assertNotNull(chain.getRequest());
    }
}
