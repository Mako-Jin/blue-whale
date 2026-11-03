package com.bluewhale.auth.controller;

import com.bluewhale.auth.application.tenant.TenantAppService;
import com.bluewhale.auth.application.tenant.TenantMembershipAppService;
import com.bluewhale.auth.application.tenant.TenantProvisioningAppService;
import com.bluewhale.auth.application.user.password.TenantPasswordPolicyService;
import com.bluewhale.auth.application.user.password.UpdateTenantPasswordPolicyCommand;
import com.bluewhale.auth.dto.TenantPasswordPolicyRequest;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.auth.vo.TenantPasswordPolicyVO;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link TenantController} 租户口令策略端点单元测试（REQ-SEC-023）。
 *
 * <p>覆盖 GET/PUT {@code /tenant/password-policy} 的 happy path、请求体范围校验失败，
 * 以及权限码与审计注解契约。</p>
 *
 * @author BlueWhale
 */
class TenantControllerTest {

    private static final long TENANT_ID = 1L;
    private static final int CUSTOM_MAX_AGE_DAYS = 60;
    private static final int CUSTOM_WARN_DAYS = 10;
    private static final int DEFAULT_MAX_AGE_DAYS = 90;
    private static final int DEFAULT_WARN_DAYS = 7;
    private static final int PARAM_ERROR_CODE = 400;

    private final TenantAppService tenantAppService = mock(TenantAppService.class);
    private final TenantProvisioningAppService tenantProvisioningAppService =
            mock(TenantProvisioningAppService.class);
    private final TenantMembershipAppService tenantMembershipAppService =
            mock(TenantMembershipAppService.class);
    private final TenantPasswordPolicyService tenantPasswordPolicyService =
            mock(TenantPasswordPolicyService.class);
    private final TenantController controller = new TenantController(tenantAppService,
            tenantProvisioningAppService, tenantMembershipAppService, tenantPasswordPolicyService);

    private MockMvc mockMvc;

    /**
     * 构建 standalone MockMvc：注册全局异常处理器与校验器
     */
    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    /**
     * 构造租户口令策略视图
     *
     * @param customized 是否租户自定义
     * @return 策略视图
     */
    private static TenantPasswordPolicyVO view(boolean customized) {
        return new TenantPasswordPolicyVO(TENANT_ID, CUSTOM_MAX_AGE_DAYS, CUSTOM_WARN_DAYS, customized,
                DEFAULT_MAX_AGE_DAYS, DEFAULT_WARN_DAYS);
    }

    @Test
    @DisplayName("GET /tenant/password-policy：返回当前租户生效策略与全局默认值")
    void shouldGetCurrentTenantPolicy() throws Exception {
        when(tenantPasswordPolicyService.current()).thenReturn(view(true));

        mockMvc.perform(get("/tenant/password-policy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.tenantId").value(TENANT_ID))
                .andExpect(jsonPath("$.data.maxAgeDays").value(CUSTOM_MAX_AGE_DAYS))
                .andExpect(jsonPath("$.data.warnDays").value(CUSTOM_WARN_DAYS))
                .andExpect(jsonPath("$.data.customized").value(true))
                .andExpect(jsonPath("$.data.defaultMaxAgeDays").value(DEFAULT_MAX_AGE_DAYS));

        verify(tenantPasswordPolicyService).current();
    }

    @Test
    @DisplayName("PUT /tenant/password-policy：校验通过后更新并返回自定义策略")
    void shouldUpdateTenantPolicy() throws Exception {
        when(tenantPasswordPolicyService.update(any())).thenReturn(view(true));

        mockMvc.perform(put("/tenant/password-policy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxAgeDays\":60,\"warnDays\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.maxAgeDays").value(CUSTOM_MAX_AGE_DAYS))
                .andExpect(jsonPath("$.data.customized").value(true));

        verify(tenantPasswordPolicyService)
                .update(new UpdateTenantPasswordPolicyCommand(CUSTOM_MAX_AGE_DAYS, CUSTOM_WARN_DAYS));
    }

    @Test
    @DisplayName("PUT /tenant/password-policy：有效期超出 1–365 天返回参数错误且不更新")
    void shouldRejectOutOfRangeMaxAgeDays() throws Exception {
        mockMvc.perform(put("/tenant/password-policy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxAgeDays\":366,\"warnDays\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(PARAM_ERROR_CODE));

        verify(tenantPasswordPolicyService, never()).update(any());
    }

    @Test
    @DisplayName("权限与审计契约：查询 sys:tenant:pwdpolicy:list、编辑 sys:tenant:pwdpolicy:edit 且留痕")
    void shouldDeclarePermissionAndAuditContract() throws NoSuchMethodException {
        RequirePermission list = TenantController.class.getMethod("passwordPolicy")
                .getAnnotation(RequirePermission.class);
        RequirePermission edit = TenantController.class
                .getMethod("updatePasswordPolicy", TenantPasswordPolicyRequest.class)
                .getAnnotation(RequirePermission.class);

        assertThat(list).as("GET /tenant/password-policy 缺少 @RequirePermission").isNotNull();
        assertThat(list.value()).isEqualTo("sys:tenant:pwdpolicy:list");
        assertThat(edit).as("PUT /tenant/password-policy 缺少 @RequirePermission").isNotNull();
        assertThat(edit.value()).isEqualTo("sys:tenant:pwdpolicy:edit");

        AuditLog auditLog = TenantController.class
                .getMethod("updatePasswordPolicy", TenantPasswordPolicyRequest.class)
                .getAnnotation(AuditLog.class);
        assertThat(auditLog).as("PUT /tenant/password-policy 缺少 @AuditLog").isNotNull();
        assertThat(auditLog.targetType()).isEqualTo("sys_tenant_password_policy");
    }
}
