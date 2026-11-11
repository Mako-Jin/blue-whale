package com.bluewhale.auth.interfaces.controller;

import com.bluewhale.auth.application.userconfig.UserConfigAppService;
import com.bluewhale.auth.application.userconfig.UserConfigBatchService;
import com.bluewhale.auth.application.userconfig.command.SaveUserConfigCommand;
import com.bluewhale.auth.application.userconfig.vo.ConfigBatchItemVO;
import com.bluewhale.common.core.handler.GlobalExceptionHandler;
import com.bluewhale.common.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link UserConfigInternalController} 控制器层单元测试（standalone MockMvc）。
 *
 * <p>覆盖 Wave 6.25 新增的内网写端点 {@code POST /user/internal/config/set}：
 * 正常写入、租户头与请求体不一致拒绝、缺租户上下文拒绝、请求体校验失败，
 * 以及既有 batch-get 的响应形状。</p>
 *
 * @author BlueWhale
 */
class UserConfigInternalControllerTest {

    private final UserConfigBatchService userConfigBatchService = mock(UserConfigBatchService.class);

    private final UserConfigAppService userConfigAppService = mock(UserConfigAppService.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new UserConfigInternalController(userConfigBatchService, userConfigAppService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.set(null);
    }

    @Test
    @DisplayName("写入配置：透传 key/value 并把 operatorId 缺省为 userId")
    void shouldUpsertWithDefaultOperator() throws Exception {
        TenantContext.set(1L);

        mockMvc.perform(post("/user/internal/config/set")
                        .header("X-Tenant-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":1,"userId":7,"configKey":"scene.view",
                                 "configValue":"{\\"viewMode\\":\\"3D\\"}"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        ArgumentCaptor<SaveUserConfigCommand> captor = ArgumentCaptor.forClass(SaveUserConfigCommand.class);
        verify(userConfigAppService).upsert(eq(1L), eq(7L), captor.capture());
        assertThat(captor.getValue().configKey()).isEqualTo("scene.view");
        assertThat(captor.getValue().updatedBy()).isEqualTo(7L);
    }

    @Test
    @DisplayName("写入配置：请求体 tenantId 与 X-Tenant-Id 不一致时拒绝，不落库")
    void shouldRejectTenantMismatch() throws Exception {
        TenantContext.set(2L);

        mockMvc.perform(post("/user/internal/config/set")
                        .header("X-Tenant-Id", "2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":1,"userId":7,"configKey":"scene.view",
                                 "configValue":"{\\"viewMode\\":\\"3D\\"}"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        verify(userConfigAppService, never()).upsert(any(), any(), any());
    }

    @Test
    @DisplayName("写入配置：缺少租户上下文时拒绝，不落库")
    void shouldRejectMissingTenantContext() throws Exception {
        mockMvc.perform(post("/user/internal/config/set")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":1,"userId":7,"configKey":"scene.view",
                                 "configValue":"{\\"viewMode\\":\\"3D\\"}"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        verify(userConfigAppService, never()).upsert(any(), any(), any());
    }

    @Test
    @DisplayName("写入配置：configKey 为空时参数校验失败")
    void shouldRejectBlankConfigKey() throws Exception {
        TenantContext.set(1L);

        mockMvc.perform(post("/user/internal/config/set")
                        .header("X-Tenant-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":1,"userId":7,"configKey":"","configValue":"{}"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        verify(userConfigAppService, never()).upsert(any(), any(), any());
    }

    @Test
    @DisplayName("批量读取：按请求键返回合并结果")
    void shouldReturnBatchItems() throws Exception {
        when(userConfigBatchService.batchGet(eq(1L), any(), any()))
                .thenReturn(List.of(new ConfigBatchItemVO(7L, "scene.view", "{\"viewMode\":\"3D\"}", "USER")));

        mockMvc.perform(post("/user/internal/config/batch-get")
                        .header("X-Tenant-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":1,\"userIds\":[7],\"keys\":[\"scene.view\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].configKey").value("scene.view"))
                .andExpect(jsonPath("$.data[0].source").value("USER"));
    }
}
