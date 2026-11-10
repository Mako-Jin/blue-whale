package com.bluewhale.common.context;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link RequestContextFilter} 单元测试：请求头解析、上下文绑定与请求结束清理。
 *
 * @author BlueWhale
 */
class RequestContextFilterTest {

    @AfterEach
    void clearContext() {
        RequestContextHolder.clear();
    }

    @Test
    @DisplayName("过滤器：解析网关注入头并在链内绑定上下文，结束后清理")
    void shouldBindHeadersAndClearAfterChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "12");
        request.addHeader("X-Username", "alice");
        request.addHeader("X-Tenant-Id", "3");
        request.addHeader("X-Trace-Id", "trace-1");
        AtomicReference<RequestContext> seen = new AtomicReference<>();
        FilterChain chain = (req, res) -> seen.set(RequestContextHolder.get());

        new RequestContextFilter().doFilter(request, new MockHttpServletResponse(), chain);

        assertNotNull(seen.get());
        assertEquals(Long.valueOf(12L), seen.get().userId());
        assertEquals("alice", seen.get().username());
        assertEquals(Long.valueOf(3L), seen.get().tenantId());
        assertEquals("trace-1", seen.get().traceId());
        assertNull(RequestContextHolder.get());
    }

    @Test
    @DisplayName("过滤器：非法用户 ID 按未提供处理，不影响其他字段")
    void shouldIgnoreInvalidUserId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "not-a-number");
        request.addHeader("X-Username", "alice");
        AtomicReference<RequestContext> seen = new AtomicReference<>();
        FilterChain chain = (req, res) -> seen.set(RequestContextHolder.get());

        new RequestContextFilter().doFilter(request, new MockHttpServletResponse(), chain);

        assertNotNull(seen.get());
        assertNull(seen.get().userId());
        assertEquals("alice", seen.get().username());
    }

    @Test
    @DisplayName("过滤器：无相关请求头时不绑定上下文")
    void shouldKeepContextEmptyWithoutHeaders() throws Exception {
        AtomicReference<RequestContext> seen = new AtomicReference<>();
        FilterChain chain = (req, res) -> seen.set(RequestContextHolder.get());

        new RequestContextFilter().doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertNull(seen.get());
        assertNull(RequestContextHolder.get());
    }
}
