package com.bluewhale.gateway.trace;

import io.micrometer.tracing.TraceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link TraceparentExchangeFilter} 单元测试：W3C traceparent 格式化。
 *
 * @author BlueWhale
 */
class TraceparentExchangeFilterTest {

    private static final String TRACE_ID = "0af7651916cd43dd8448eb211c80319c";
    private static final String SPAN_ID = "b7ad6b7169203331";

    @Test
    @DisplayName("已采样：格式为 00-traceId-spanId-01")
    void shouldFormatSampledTraceparent() {
        TraceContext context = mock(TraceContext.class);
        when(context.traceId()).thenReturn(TRACE_ID);
        when(context.spanId()).thenReturn(SPAN_ID);
        when(context.sampled()).thenReturn(Boolean.TRUE);

        assertEquals("00-" + TRACE_ID + "-" + SPAN_ID + "-01", TraceparentExchangeFilter.traceparent(context));
    }

    @Test
    @DisplayName("未采样：标记位为 00")
    void shouldFormatNotSampledTraceparent() {
        TraceContext context = mock(TraceContext.class);
        when(context.traceId()).thenReturn(TRACE_ID);
        when(context.spanId()).thenReturn(SPAN_ID);
        when(context.sampled()).thenReturn(Boolean.FALSE);

        assertEquals("00-" + TRACE_ID + "-" + SPAN_ID + "-00", TraceparentExchangeFilter.traceparent(context));
    }

    @Test
    @DisplayName("无活动 span：返回 null，不注入头")
    void shouldReturnNullWithoutTraceContext() {
        assertNull(TraceparentExchangeFilter.traceparent((TraceContext) null));
        assertNull(TraceparentExchangeFilter.traceparent((io.micrometer.tracing.Tracer) null));
    }
}
