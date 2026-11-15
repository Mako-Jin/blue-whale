package com.bluewhale.file.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.handler.GlobalExceptionHandler;
import com.bluewhale.file.config.FileProperties;
import com.bluewhale.file.model.request.UploadPolicyUpdateRequest;
import com.bluewhale.file.service.support.UploadPolicyRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.accept.ApiVersionStrategy;
import org.springframework.web.accept.DefaultApiVersionStrategy;
import org.springframework.web.accept.PathApiVersionResolver;
import org.springframework.web.accept.SemanticApiVersionParser;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link FileUploadPolicyController} 上传策略热更新接口单元测试（standalone MockMvc）。
 *
 * <p>权限拦截器不在 standalone 环境装配，管理权限通过反射校验实现方法上的
 * {@code @RequirePermission} 注解。</p>
 *
 * @author BlueWhale
 */
class FileUploadPolicyControllerTest {

    /** 文件管理权限码 */
    private static final String PERM_FILE_MANAGE = "sys:file:manage";

    private static final long DEFAULT_MAX_SIZE = 52428800L;

    private final FileProperties fileProperties = new FileProperties();

    private final UploadPolicyRegistry uploadPolicyRegistry = new UploadPolicyRegistry(fileProperties);

    private final FileUploadPolicyController controller =
            new FileUploadPolicyController(fileProperties, uploadPolicyRegistry);

    private MockMvc mockMvc;

    /**
     * 构建 standalone MockMvc：仅注册控制器、校验器与全局异常处理器
     */
    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        ApiVersionStrategy versionStrategy = new DefaultApiVersionStrategy(
                List.of(new PathApiVersionResolver(1, requestPath -> true)),
                new SemanticApiVersionParser(), false, null, true, null, null);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(validator)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setApiVersionStrategy(versionStrategy)
                .build();
    }

    @Test
    @DisplayName("GET /file/v1/upload-policy：返回开关、全局默认值与当前规则")
    void shouldReturnCurrentPolicy() throws Exception {
        mockMvc.perform(get("/file/v1/upload-policy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.defaultMaxSizeBytes").value(DEFAULT_MAX_SIZE))
                .andExpect(jsonPath("$.data.rules.length()").value(3))
                .andExpect(jsonPath("$.data.rules[0].permission").value("sys:video:upload"))
                .andExpect(jsonPath("$.data.rules[0].maxSizeBytes").value(2147483648L));
    }

    @Test
    @DisplayName("PUT /file/v1/upload-policy：整表替换并返回更新后视图")
    void shouldReplaceRules() throws Exception {
        mockMvc.perform(put("/file/v1/upload-policy")
                        .contentType("application/json")
                        .content("{\"rules\":[{\"permission\":\"sys:file:upload\","
                                + "\"maxSizeBytes\":209715200,\"quotaBytes\":0}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.rules.length()").value(1))
                .andExpect(jsonPath("$.data.rules[0].permission").value("sys:file:upload"))
                .andExpect(jsonPath("$.data.rules[0].maxSizeBytes").value(209715200L))
                .andExpect(jsonPath("$.data.rules[0].quotaBytes").value(0));

        assertEquals(1, uploadPolicyRegistry.rules().size());
        assertEquals("sys:file:upload", uploadPolicyRegistry.rules().get(0).getPermission());
        assertEquals(209715200L, uploadPolicyRegistry.rules().get(0).getMaxSizeBytes());
    }

    @Test
    @DisplayName("PUT /file/v1/upload-policy：空白权限码校验失败且不改动注册表")
    void shouldRejectBlankPermission() throws Exception {
        mockMvc.perform(put("/file/v1/upload-policy")
                        .contentType("application/json")
                        .content("{\"rules\":[{\"permission\":\" \",\"maxSizeBytes\":1024}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));

        assertEquals(3, uploadPolicyRegistry.rules().size());
    }

    @Test
    @DisplayName("PUT /file/v1/upload-policy：空规则列表校验失败且不改动注册表")
    void shouldRejectEmptyRules() throws Exception {
        mockMvc.perform(put("/file/v1/upload-policy")
                        .contentType("application/json")
                        .content("{\"rules\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));

        assertEquals(3, uploadPolicyRegistry.rules().size());
    }

    @Test
    @DisplayName("GET/PUT 均要求 sys:file:manage 权限码")
    void shouldRequireManagePermission() throws Exception {
        assertManagePermission(FileUploadPolicyController.class.getMethod("get"));
        assertManagePermission(FileUploadPolicyController.class.getMethod("update",
                UploadPolicyUpdateRequest.class));
    }

    /**
     * 校验方法上的权限注解
     *
     * @param method 控制器方法
     */
    private static void assertManagePermission(Method method) {
        RequirePermission annotation = method.getAnnotation(RequirePermission.class);
        assertEquals(PERM_FILE_MANAGE, annotation == null ? null : annotation.value());
    }
}
