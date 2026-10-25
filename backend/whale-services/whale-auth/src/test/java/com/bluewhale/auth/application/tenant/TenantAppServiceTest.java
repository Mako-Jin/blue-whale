package com.bluewhale.auth.application.tenant;

import com.bluewhale.auth.application.tenant.command.ChangeTenantStatusCommand;
import com.bluewhale.auth.application.tenant.command.RenewTenantCommand;
import com.bluewhale.auth.application.tenant.query.TenantPageQuery;
import com.bluewhale.auth.domain.tenant.aggregate.Tenant;
import com.bluewhale.auth.domain.tenant.repository.TenantRepository;
import com.bluewhale.auth.security.TenantContext;
import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @BeforeEach
    void bindTenant() {
        TenantContext.set(1L);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("租户编码为空：回落默认租户 default")
    void shouldFallbackToDefaultTenantWhenCodeBlank() {
        when(tenantRepository.findByCode(TenantAppService.DEFAULT_TENANT_CODE))
                .thenReturn(Optional.of(Tenant.reconstitute(1L, "default", "默认租户", "PLATFORM", 1,
                        null, null, null, null)));

        assertEquals(1L, service.resolveEnabledTenantId("  "));
    }

    @Test
    @DisplayName("租户存在且启用：返回租户 ID")
    void shouldResolveEnabledTenant() {
        when(tenantRepository.findByCode("acme"))
                .thenReturn(Optional.of(Tenant.reconstitute(TENANT_ID, "acme", "ACME", "PLATFORM", 1,
                        null, null, null, null)));

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
                .thenReturn(Optional.of(Tenant.reconstitute(TENANT_ID, "acme", "ACME", "PLATFORM", 0,
                        null, null, null, null)));

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
                .thenReturn(Optional.of(Tenant.reconstitute(TENANT_ID, "acme", "ACME", "PLATFORM", 1,
                        null, null, null, null)));

        service.ensureEnabled(TENANT_ID);
    }

    @Test
    @DisplayName("租户已过期：返回 401")
    void shouldRejectExpiredTenant() {
        when(tenantRepository.findByCode("acme"))
                .thenReturn(Optional.of(Tenant.reconstitute(TENANT_ID, "acme", "ACME", "PLATFORM", 1,
                        LocalDateTime.now().minusDays(1), null, null, null)));

        BizException ex = assertThrows(BizException.class, () -> service.resolveEnabledTenantId("acme"));

        assertEquals(401, ex.getCode());
    }

    @Test
    @DisplayName("分页列表：非平台租户返回 403")
    void shouldRejectPageWhenTenantNotPlatform() {
        when(tenantRepository.findById(1L))
                .thenReturn(Optional.of(Tenant.reconstitute(1L, "acme", "ACME", "TENANT", 1,
                        null, null, null, null)));

        BizException ex = assertThrows(BizException.class,
                () -> service.page(new TenantPageQuery(1, 10, null, null)));

        assertEquals(403, ex.getCode());
    }

    @Test
    @DisplayName("平台租户停用自身：返回参数异常")
    void shouldRejectDisablingPlatformTenant() {
        when(tenantRepository.findById(1L))
                .thenReturn(Optional.of(Tenant.reconstitute(1L, "default", "默认租户", "PLATFORM", 1,
                        null, null, null, null)));

        BizException ex = assertThrows(BizException.class,
                () -> service.changeStatus(1L, new ChangeTenantStatusCommand(0)));

        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("平台租户不允许停用"));
    }

    @Test
    @DisplayName("续期：到期时间早于当前时间返回参数异常")
    void shouldRejectRenewWithPastExpireTime() {
        when(tenantRepository.findById(1L))
                .thenReturn(Optional.of(Tenant.reconstitute(1L, "default", "默认租户", "PLATFORM", 1,
                        null, null, null, null)));

        BizException ex = assertThrows(BizException.class,
                () -> service.renew(1L, new RenewTenantCommand(LocalDateTime.now().minusDays(1))));

        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("续期时间必须晚于当前时间"));
    }
}
