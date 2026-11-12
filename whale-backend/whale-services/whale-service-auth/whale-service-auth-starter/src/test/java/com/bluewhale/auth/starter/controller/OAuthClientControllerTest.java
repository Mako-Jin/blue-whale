package com.bluewhale.auth.starter.controller;

import com.bluewhale.auth.application.model.vo.OAuthClientSecretVO;
import com.bluewhale.auth.application.service.OAuthClientAppService;
import com.bluewhale.auth.interfaces.dto.request.OAuthClientSaveRequest;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.auth.starter.assemble.OAuthClientAssembler;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.handler.GlobalExceptionHandler;
import com.bluewhale.common.core.query.PageQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.accept.ApiVersionStrategy;
import org.springframework.web.accept.DefaultApiVersionStrategy;
import org.springframework.web.accept.PathApiVersionResolver;
import org.springframework.web.accept.SemanticApiVersionParser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link OAuthClientController} 端点单元测试（权限中心扩展 M0）。
 *
 * <p>覆盖客户端注册（密钥仅返回一次）、请求校验，以及权限码/审计注解契约。</p>
 *
 * @author BlueWhale
 */
class OAuthClientControllerTest {

    private static final int PARAM_ERROR_CODE = 400;

    private final OAuthClientAppService oauthClientAppService = mock(OAuthClientAppService.class);
    private final OAuthClientController controller = new OAuthClientController(oauthClientAppService,
            Mappers.getMapper(OAuthClientAssembler.class));

    private MockMvc mockMvc;

    /**
     * 构建 standalone MockMvc：注册全局异常处理器与校验器
     */
    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        ApiVersionStrategy versionStrategy = new DefaultApiVersionStrategy(
                java.util.List.of(new PathApiVersionResolver(1, requestPath -> true)),
                new SemanticApiVersionParser(), false, null, true, null, null);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setApiVersionStrategy(versionStrategy)
                .build();
    }

    @Test
    @DisplayName("POST /oauth-client/v1：注册成功仅返回一次密钥明文")
    void shouldReturnSecretOnceOnCreate() throws Exception {
        when(oauthClientAppService.create(any()))
                .thenReturn(new OAuthClientSecretVO(10L, "bluewhale-web", "raw-secret"));

        mockMvc.perform(post("/oauth-client/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientName\":\"BlueWhale Web\","
                                + "\"redirectUris\":[\"https://app.example.com/callback\"],"
                                + "\"grantTypes\":[\"authorization_code\",\"refresh_token\"],"
                                + "\"scopes\":[\"openid\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.clientId").value("bluewhale-web"))
                .andExpect(jsonPath("$.data.clientSecret").value("raw-secret"));

        verify(oauthClientAppService).create(any());
    }

    @Test
    @DisplayName("POST /oauth-client/v1：授权类型为空返回参数错误且不注册")
    void shouldRejectEmptyGrantTypes() throws Exception {
        mockMvc.perform(post("/oauth-client/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientName\":\"BlueWhale Web\",\"grantTypes\":[],"
                                + "\"scopes\":[\"openid\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(PARAM_ERROR_CODE));

        verify(oauthClientAppService, never()).create(any());
    }

    @Test
    @DisplayName("权限与审计契约：声明 sys:oauth:client:* 且密钥操作不记录请求参数")
    void shouldDeclarePermissionAndAuditContract() throws NoSuchMethodException {
        assertThat(OAuthClientController.class.getMethod("page", PageQuery.class, String.class, Integer.class)
                .getAnnotation(RequirePermission.class).value()).isEqualTo("sys:oauth:client:list");
        assertThat(OAuthClientController.class.getMethod("create", OAuthClientSaveRequest.class)
                .getAnnotation(RequirePermission.class).value()).isEqualTo("sys:oauth:client:add");
        assertThat(OAuthClientController.class.getMethod("update", Long.class, OAuthClientSaveRequest.class)
                .getAnnotation(RequirePermission.class).value()).isEqualTo("sys:oauth:client:edit");
        assertThat(OAuthClientController.class.getMethod("rotateSecret", Long.class)
                .getAnnotation(RequirePermission.class).value()).isEqualTo("sys:oauth:client:secret");
        assertThat(OAuthClientController.class.getMethod("disable", Long.class)
                .getAnnotation(RequirePermission.class).value()).isEqualTo("sys:oauth:client:delete");

        AuditLog audit = OAuthClientController.class.getMethod("rotateSecret", Long.class)
                .getAnnotation(AuditLog.class);
        assertThat(audit).isNotNull();
        assertThat(audit.recordParams()).isFalse();
    }
}
