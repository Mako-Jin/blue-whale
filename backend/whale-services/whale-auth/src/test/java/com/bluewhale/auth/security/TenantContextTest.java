package com.bluewhale.auth.security;

import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TenantContext} 单元测试（Wave 5.54 Phase 1）：线程级租户上下文绑定、读取与清理。
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
}
