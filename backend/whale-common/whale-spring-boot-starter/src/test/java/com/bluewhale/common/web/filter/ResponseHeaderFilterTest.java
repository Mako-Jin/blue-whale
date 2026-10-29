package com.bluewhale.common.web.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 安全响应头过滤器测试。
 *
 * @author BlueWhale
 */
class ResponseHeaderFilterTest {

    @Test
    @DisplayName("写入禁缓存与安全响应头并继续过滤器链")
    void shouldAddSecurityHeadersAndContinueChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/demo");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chained = new AtomicBoolean(false);

        new ResponseHeaderFilter().doFilter(request, response, (req, res) -> chained.set(true));

        assertTrue(chained.get(), "必须继续执行过滤器链");
        assertEquals("no-store, no-cache, must-revalidate", response.getHeader("Cache-Control"));
        assertEquals("no-cache", response.getHeader("Pragma"));
        assertEquals("0", response.getHeader("Expires"));
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertEquals("DENY", response.getHeader("X-Frame-Options"));
        assertEquals("1; mode=block", response.getHeader("X-XSS-Protection"));
    }
}
