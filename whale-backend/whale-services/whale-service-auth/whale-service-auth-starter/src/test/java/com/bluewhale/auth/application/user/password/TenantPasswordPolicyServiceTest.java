package com.bluewhale.auth.application.user.password;

import com.bluewhale.auth.application.tenant.TenantAppService;
import com.bluewhale.auth.application.user.password.port.TenantPasswordPolicyRepository;
import com.bluewhale.auth.application.vo.TenantPasswordPolicyVO;
import com.bluewhale.auth.infrastructure.config.PasswordPolicyProperties;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TenantPasswordPolicyService} 单元测试（REQ-SEC-023）。
 *
 * <p>覆盖租户自定义策略读取、未配置回退全局默认、可配置范围校验、更新落库与
 * 全量启用租户生效策略合并。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class TenantPasswordPolicyServiceTest {

    private static final long TENANT_ID = 1L;
    private static final int CUSTOM_MAX_AGE_DAYS = 60;
    private static final int CUSTOM_WARN_DAYS = 10;
    private static final int PARAM_ERROR_CODE = 400;

    @Mock
    private TenantPasswordPolicyRepository tenantPasswordPolicyRepository;

    @Mock
    private TenantAppService tenantAppService;

    private PasswordPolicyProperties properties;

    private TenantPasswordPolicyService service;

    @BeforeEach
    void setUp() {
        properties = new PasswordPolicyProperties();
        service = new TenantPasswordPolicyService(properties, tenantPasswordPolicyRepository, tenantAppService);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("effectivePolicy：租户已配置时返回自定义策略")
    void shouldReturnConfiguredPolicy() {
        when(tenantPasswordPolicyRepository.findByTenantId(TENANT_ID))
                .thenReturn(Optional.of(new TenantPasswordPolicy(TENANT_ID, CUSTOM_MAX_AGE_DAYS, CUSTOM_WARN_DAYS)));

        TenantPasswordPolicy policy = service.effectivePolicy(TENANT_ID);

        assertEquals(CUSTOM_MAX_AGE_DAYS, policy.maxAgeDays());
        assertEquals(CUSTOM_WARN_DAYS, policy.warnDays());
        assertEquals(CUSTOM_MAX_AGE_DAYS, service.maxAgeDays(TENANT_ID));
    }

    @Test
    @DisplayName("effectivePolicy：租户未配置时回退全局默认，tenantId 为空同样回退")
    void shouldFallbackToGlobalDefault() {
        when(tenantPasswordPolicyRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.empty());

        assertEquals(properties.getMaxAgeDays(), service.effectivePolicy(TENANT_ID).maxAgeDays());
        assertEquals(properties.getMaxAgeDays(), service.effectivePolicy(null).maxAgeDays());
        verify(tenantPasswordPolicyRepository, never()).findByTenantId(null);
    }

    @Test
    @DisplayName("current：返回当前登录租户生效策略，未自定义时 customized=false")
    void shouldReturnCurrentTenantPolicy() {
        TenantContext.set(TENANT_ID);
        when(tenantPasswordPolicyRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.empty());

        TenantPasswordPolicyVO view = service.current();

        assertEquals(TENANT_ID, view.getTenantId());
        assertEquals(properties.getMaxAgeDays(), view.getMaxAgeDays());
        assertEquals(properties.getWarnDays(), view.getWarnDays());
        assertFalse(view.isCustomized());
        assertEquals(properties.getMaxAgeDays(), view.getDefaultMaxAgeDays());
    }

    @Test
    @DisplayName("getEffective：已自定义时 customized=true 并携带全局默认值")
    void shouldMarkCustomizedInView() {
        TenantPasswordPolicy policy = new TenantPasswordPolicy(TENANT_ID, CUSTOM_MAX_AGE_DAYS, CUSTOM_WARN_DAYS);
        when(tenantPasswordPolicyRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.of(policy));

        TenantPasswordPolicyVO view = service.getEffective(TENANT_ID);

        assertTrue(view.isCustomized());
        assertEquals(CUSTOM_MAX_AGE_DAYS, view.getMaxAgeDays());
        assertEquals(properties.getMaxAgeDays(), view.getDefaultMaxAgeDays());
    }

    @Test
    @DisplayName("update：校验通过后落库并返回自定义视图")
    void shouldUpdateTenantPolicy() {
        TenantContext.set(TENANT_ID);
        when(tenantPasswordPolicyRepository.save(any(TenantPasswordPolicy.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TenantPasswordPolicyVO view = service.update(
                new UpdateTenantPasswordPolicyCommand(CUSTOM_MAX_AGE_DAYS, CUSTOM_WARN_DAYS));

        assertTrue(view.isCustomized());
        assertEquals(CUSTOM_MAX_AGE_DAYS, view.getMaxAgeDays());
        assertEquals(CUSTOM_WARN_DAYS, view.getWarnDays());
        verify(tenantPasswordPolicyRepository).save(new TenantPasswordPolicy(TENANT_ID,
                CUSTOM_MAX_AGE_DAYS, CUSTOM_WARN_DAYS));
    }

    @Test
    @DisplayName("update：有效期越界或提醒天数超过有效期均拒绝，且不落库")
    void shouldRejectInvalidPolicy() {
        TenantContext.set(TENANT_ID);

        BizException tooLarge = assertThrows(BizException.class,
                () -> service.update(new UpdateTenantPasswordPolicyCommand(366, 7)));
        BizException tooSmall = assertThrows(BizException.class,
                () -> service.update(new UpdateTenantPasswordPolicyCommand(0, 0)));
        BizException warnTooLarge = assertThrows(BizException.class,
                () -> service.update(new UpdateTenantPasswordPolicyCommand(10, 11)));
        BizException missing = assertThrows(BizException.class,
                () -> service.update(new UpdateTenantPasswordPolicyCommand(null, 1)));

        assertEquals(PARAM_ERROR_CODE, tooLarge.getCode());
        assertEquals(PARAM_ERROR_CODE, tooSmall.getCode());
        assertEquals(PARAM_ERROR_CODE, warnTooLarge.getCode());
        assertEquals(PARAM_ERROR_CODE, missing.getCode());
        verify(tenantPasswordPolicyRepository, never()).save(any());
    }

    @Test
    @DisplayName("effectivePolicies：仅返回启用租户，已配置取值、未配置回退默认")
    void shouldMergePoliciesForEnabledTenants() {
        when(tenantAppService.listEnabledTenantIds()).thenReturn(List.of(1L, 2L));
        when(tenantPasswordPolicyRepository.findAll())
                .thenReturn(List.of(new TenantPasswordPolicy(1L, CUSTOM_MAX_AGE_DAYS, CUSTOM_WARN_DAYS)));

        Map<Long, TenantPasswordPolicy> policies = service.effectivePolicies();

        assertEquals(2, policies.size());
        assertEquals(CUSTOM_MAX_AGE_DAYS, policies.get(1L).maxAgeDays());
        assertEquals(properties.getMaxAgeDays(), policies.get(2L).maxAgeDays());
        assertEquals(properties.getWarnDays(), policies.get(2L).warnDays());
    }
}
