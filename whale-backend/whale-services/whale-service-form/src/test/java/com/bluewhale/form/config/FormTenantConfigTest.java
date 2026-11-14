package com.bluewhale.form.config;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.tenant.TenantContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FormTenantConfig.TenantGuardInterceptor} 租户上下文强制校验单元测试。
 *
 * @author BlueWhale
 */
class FormTenantConfigTest {

    private static final int PARAM_ERROR_CODE = 400;

    private final FormTenantConfig.TenantGuardInterceptor interceptor =
            new FormTenantConfig.TenantGuardInterceptor();

    @Test
    @DisplayName("preHandle：租户上下文缺失时拒绝")
    void shouldRejectWhenTenantMissing() {
        TenantContext.clear();

        BizException ex = assertThrows(BizException.class, () -> interceptor.preHandle(null, null, null));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
    }

    @Test
    @DisplayName("preHandle：租户上下文存在时放行")
    void shouldPassWhenTenantPresent() {
        TenantContext.set(2L);
        try {
            assertTrue(interceptor.preHandle(null, null, null));
        } finally {
            TenantContext.clear();
        }
    }
}
