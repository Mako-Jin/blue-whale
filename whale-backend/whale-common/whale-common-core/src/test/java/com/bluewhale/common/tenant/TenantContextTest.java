package com.bluewhale.common.tenant;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 租户上下文读写、缺失失败关闭与线程隔离测试。
 *
 * <p>核心断言：缺失上下文时 {@link TenantContext#requireId()} 必须抛异常而不是回落默认租户
 * （失败关闭），{@link TenantContext#segment()} 才允许返回未知租户段 0。</p>
 */
class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("set 后 get 返回绑定的租户；clear 后回到未绑定")
    void shouldBindAndClear() {
        assertNull(TenantContext.get(), "初始应为未绑定");

        TenantContext.set(1001L);
        assertEquals(1001L, TenantContext.get());

        TenantContext.clear();
        assertNull(TenantContext.get());
    }

    @Test
    @DisplayName("set(null) 等价于解绑，而不是绑定 null")
    void shouldUnbindOnNull() {
        TenantContext.set(1001L);
        TenantContext.set(null);

        assertNull(TenantContext.get());
    }

    @Test
    @DisplayName("requireId：已绑定时返回租户 ID")
    void shouldReturnIdWhenPresent() {
        TenantContext.set(2002L);

        assertEquals(2002L, TenantContext.requireId());
    }

    @Test
    @DisplayName("requireId：未绑定时抛业务异常，不回落到默认租户")
    void shouldThrowWhenMissing() {
        BizException ex = assertThrows(BizException.class, TenantContext::requireId);

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("租户上下文"));
    }

    @Test
    @DisplayName("segment：已绑定返回租户 ID 文本，未绑定返回未知租户 0")
    void shouldRenderSegment() {
        assertEquals(String.valueOf(TenantContext.UNKNOWN_TENANT_ID), TenantContext.segment());

        TenantContext.set(3003L);
        assertEquals("3003", TenantContext.segment());
    }

    @Test
    @DisplayName("租户上下文按线程隔离，不串到其它线程")
    void shouldIsolateBetweenThreads() throws Exception {
        TenantContext.set(4004L);

        AtomicReference<Long> otherThreadValue = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Thread worker = new Thread(() -> {
            otherThreadValue.set(TenantContext.get());
            done.countDown();
        });
        worker.start();
        assertTrue(done.await(5, TimeUnit.SECONDS), "子线程应在 5 秒内完成");

        assertNull(otherThreadValue.get(), "子线程不应看到主线程的租户");
        assertEquals(4004L, TenantContext.get(), "子线程不应污染主线程的租户");
    }

    @Test
    @DisplayName("常量语义：默认租户为 1、未知租户为 0")
    void shouldKeepConstantSemantics() {
        assertEquals(1L, TenantContext.DEFAULT_TENANT_ID);
        assertEquals(0L, TenantContext.UNKNOWN_TENANT_ID);
    }
}
