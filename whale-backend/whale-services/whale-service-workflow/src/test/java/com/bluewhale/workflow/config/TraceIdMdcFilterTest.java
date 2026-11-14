package com.bluewhale.workflow.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link TraceIdMdcFilter} 单元测试：MDC 写入与请求结束清理。
 *
 * @author BlueWhale
 */
class TraceIdMdcFilterTest {

    @Test
    @DisplayName("携带 X-Trace-Id 时链内 MDC 可见，结束后清理")
    void shouldPutTraceIdIntoMdcAndClear() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id", "trace-9");
        AtomicReference<String> seen = new AtomicReference<>();
        TraceIdMdcFilter filter = new TraceIdMdcFilter();

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> seen.set(MDC.get(TraceIdMdcFilter.MDC_TRACE_ID)));

        assertEquals("trace-9", seen.get());
        assertNull(MDC.get(TraceIdMdcFilter.MDC_TRACE_ID));
    }

    @Test
    @DisplayName("缺少 X-Trace-Id 时不写入 MDC")
    void shouldNotPutMdcWhenHeaderMissing() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>("unset");
        TraceIdMdcFilter filter = new TraceIdMdcFilter();

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (req, res) -> seen.set(MDC.get(TraceIdMdcFilter.MDC_TRACE_ID)));

        assertNull(seen.get());
        assertNull(MDC.get(TraceIdMdcFilter.MDC_TRACE_ID));
    }
}
