package com.bluewhale.common.tenant;

import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TenantContext} 单元测试（Wave 5.54 Phase 2）：线程级租户上下文绑定、读取与清理。
 *
 * @author BlueWhale
 */
class TenantContextTest {

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("requireId：缺失上下文时返回 400")
    void shouldRejectRequireIdWhenMissing() {
        TenantContext.clear();

        BizException ex = assertThrows(BizException.class, TenantContext::requireId);

        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("缺少租户上下文"));
    }

    @Test
    @DisplayName("set 后 get/requireId 返回绑定的租户 ID")
    void shouldReturnBoundTenantId() {
        TenantContext.set(1L);

        assertEquals(Long.valueOf(1L), TenantContext.get());
        assertEquals(Long.valueOf(1L), TenantContext.requireId());
    }

    @Test
    @DisplayName("clear 后上下文为空")
    void shouldClearTenantContext() {
        TenantContext.set(1L);

        TenantContext.clear();

        assertNull(TenantContext.get());
        assertThrows(BizException.class, TenantContext::requireId);
    }

    @Test
    @DisplayName("segment：缺失上下文回落 0，存在时返回租户 ID 文本")
    void shouldBuildTenantSegment() {
        assertEquals("0", TenantContext.segment());

        TenantContext.set(9L);

        assertEquals("9", TenantContext.segment());
    }

    @Test
    @DisplayName("TenantKeys：缓存与锁 Key 均带租户段")
    void shouldPrefixKeysWithTenant() {
        TenantContext.set(7L);

        assertEquals("whale:7:cache:auth:user:perm:12",
                TenantKeys.cacheKey("auth:user:perm", "12"));
        assertEquals("whale:7:lock:auth:role", TenantKeys.lockPrefix("lock", "auth:role"));
    }

    @Test
    @DisplayName("TaskDecorator：异步线程继承提交线程租户并在结束后清理")
    void shouldPropagateTenantToAsyncThread() throws Exception {
        TenantContext.set(3L);
        TenantContextTaskDecorator decorator = new TenantContextTaskDecorator();
        java.util.concurrent.atomic.AtomicReference<Long> seen = new java.util.concurrent.atomic.AtomicReference<>();

        Thread thread = new Thread(decorator.decorate(() -> seen.set(TenantContext.get())));
        thread.start();
        thread.join();

        assertEquals(Long.valueOf(3L), seen.get());
    }
}
