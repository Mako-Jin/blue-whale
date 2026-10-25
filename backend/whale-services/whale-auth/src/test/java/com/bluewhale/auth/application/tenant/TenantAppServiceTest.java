package com.bluewhale.auth.application.tenant;

import com.bluewhale.auth.domain.tenant.aggregate.Tenant;
import com.bluewhale.auth.domain.tenant.repository.TenantRepository;
import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * {@link TenantAppService} 单元测试（Wave 5.54 Phase 0）：登录租户解析与启用校验。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class TenantAppServiceTest {

    private static final long TENANT_ID = 2L;

    @Mock
    private TenantRepository tenantRepository;

    @InjectMocks
    private TenantAppService service;

    @Test
    @DisplayName("租户编码为空：回落默认租户 default")
    void shouldFallbackToDefaultTenantWhenCodeBlank() {
        when(tenantRepository.findByCode(TenantAppService.DEFAULT_TENANT_CODE))
                .thenReturn(Optional.of(Tenant.reconstitute(1L, "default", "默认租户", 1)));

        assertEquals(1L, service.resolveEnabledTenantId("  "));
    }

    @Test
    @DisplayName("租户存在且启用：返回租户 ID")
    void shouldResolveEnabledTenant() {
        when(tenantRepository.findByCode("acme"))
                .thenReturn(Optional.of(Tenant.reconstitute(TENANT_ID, "acme", "ACME", 1)));

        assertEquals(TENANT_ID, service.resolveEnabledTenantId("acme"));
    }

    @Test
    @DisplayName("租户不存在：返回 401")
    void shouldRejectUnknownTenant() {
        when(tenantRepository.findByCode("ghost")).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class, () -> service.resolveEnabledTenantId("ghost"));

        assertEquals(401, ex.getCode());
    }

    @Test
    @DisplayName("租户已停用：返回 401")
    void shouldRejectDisabledTenant() {
        when(tenantRepository.findByCode("acme"))
                .thenReturn(Optional.of(Tenant.reconstitute(TENANT_ID, "acme", "ACME", 0)));

        BizException ex = assertThrows(BizException.class, () -> service.resolveEnabledTenantId("acme"));

        assertEquals(401, ex.getCode());
    }

    @Test
    @DisplayName("ensureEnabled：租户不存在时不放行")
    void shouldRejectEnsureWhenTenantMissing() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

        assertThrows(BizException.class, () -> service.ensureEnabled(TENANT_ID));
    }

    @Test
    @DisplayName("ensureEnabled：租户启用时通过")
    void shouldPassEnsureWhenTenantEnabled() {
        when(tenantRepository.findById(TENANT_ID))
                .thenReturn(Optional.of(Tenant.reconstitute(TENANT_ID, "acme", "ACME", 1)));

        service.ensureEnabled(TENANT_ID);
    }
}
