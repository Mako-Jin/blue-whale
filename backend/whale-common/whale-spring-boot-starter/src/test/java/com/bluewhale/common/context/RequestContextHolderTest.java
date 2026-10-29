package com.bluewhale.common.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link RequestContextHolder} 单元测试：线程级请求上下文绑定、分字段读取与清理。
 *
 * @author BlueWhale
 */
class RequestContextHolderTest {

    @AfterEach
    void clearContext() {
        RequestContextHolder.clear();
    }

    @Test
    @DisplayName("set 后各字段可读取")
    void shouldExposeBoundFields() {
        RequestContextHolder.set(new RequestContext(12L, "alice", 3L, "trace-1"));

        assertEquals(Long.valueOf(12L), RequestContextHolder.getUserId());
        assertEquals("alice", RequestContextHolder.getUsername());
        assertEquals(Long.valueOf(3L), RequestContextHolder.getTenantId());
        assertEquals("trace-1", RequestContextHolder.getTraceId());
    }

    @Test
    @DisplayName("空上下文不绑定，读取返回 null")
    void shouldIgnoreEmptyContext() {
        RequestContextHolder.set(new RequestContext(null, null, null, null));

        assertNull(RequestContextHolder.get());
        assertNull(RequestContextHolder.getUserId());
    }

    @Test
    @DisplayName("clear 后上下文为空")
    void shouldClearContext() {
        RequestContextHolder.set(new RequestContext(12L, "alice", 3L, "trace-1"));

        RequestContextHolder.clear();

        assertNull(RequestContextHolder.get());
        assertNull(RequestContextHolder.getUsername());
    }
}
