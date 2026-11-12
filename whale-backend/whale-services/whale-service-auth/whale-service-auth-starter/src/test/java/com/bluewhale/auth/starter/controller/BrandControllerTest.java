package com.bluewhale.auth.starter.controller;

import com.bluewhale.auth.application.model.query.BrandResolveQuery;
import com.bluewhale.auth.application.model.vo.BrandConfigVO;
import com.bluewhale.auth.application.service.BrandAppService;
import com.bluewhale.auth.interfaces.dto.request.BrandConfigSaveRequest;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.auth.starter.assemble.BrandAssembler;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.handler.GlobalExceptionHandler;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link BrandController} 端点单元测试（权限中心扩展 M0）。
 *
 * <p>覆盖登录页品牌解析、保存请求校验，以及权限码/审计注解契约。</p>
 *
 * @author BlueWhale
 */
class BrandControllerTest {

    private static final int PARAM_ERROR_CODE = 400;

    private final BrandAppService brandAppService = mock(BrandAppService.class);
    private final BrandController controller = new BrandController(brandAppService,
            Mappers.getMapper(BrandAssembler.class));

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

    /**
     * 构造登录页品牌视图
     *
     * @return 品牌视图
     */
    private static BrandConfigVO view() {
        BrandConfigVO vo = new BrandConfigVO();
        vo.setId(1L);
        vo.setTargetType("PLATFORM");
        vo.setTargetId(0L);
        vo.setDomain("");
        vo.setBrandName("BlueWhale");
        vo.setLoginTitle("BlueWhale 蓝鲸平台");
        vo.setPrimaryColor("#1677ff");
        vo.setStatus(1);
        return vo;
    }

    @Test
    @DisplayName("GET /brand/v1/resolve：免登录返回品牌渲染字段")
    void shouldResolveBrand() throws Exception {
        when(brandAppService.resolve(new BrandResolveQuery("TENANT", 2L, "auth.example.com"))).thenReturn(view());

        mockMvc.perform(get("/brand/v1/resolve")
                        .param("targetType", "TENANT")
                        .param("targetId", "2")
                        .param("domain", "auth.example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.brandName").value("BlueWhale"))
                .andExpect(jsonPath("$.data.primaryColor").value("#1677ff"));

        verify(brandAppService).resolve(new BrandResolveQuery("TENANT", 2L, "auth.example.com"));
    }

    @Test
    @DisplayName("POST /brand/v1：品牌名称为空返回参数错误且不落库")
    void shouldRejectBlankBrandName() throws Exception {
        mockMvc.perform(post("/brand/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"TENANT\",\"targetId\":2,\"brandName\":\" \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(PARAM_ERROR_CODE));

        verify(brandAppService, never()).create(any());
    }

    @Test
    @DisplayName("POST /brand/v1：主题色非法返回参数错误")
    void shouldRejectInvalidPrimaryColor() throws Exception {
        mockMvc.perform(post("/brand/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"TENANT\",\"targetId\":2,\"brandName\":\"BlueWhale\","
                                + "\"primaryColor\":\"blue\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(PARAM_ERROR_CODE));

        verify(brandAppService, never()).create(any());
    }

    @Test
    @DisplayName("权限与审计契约：管理端点声明 sys:brand:* 且写操作留痕")
    void shouldDeclarePermissionAndAuditContract() throws NoSuchMethodException {
        RequirePermission list = BrandController.class.getMethod("page",
                com.bluewhale.common.core.query.PageQuery.class, String.class, String.class, Integer.class)
                .getAnnotation(RequirePermission.class);
        RequirePermission add = BrandController.class.getMethod("create", BrandConfigSaveRequest.class)
                .getAnnotation(RequirePermission.class);
        RequirePermission edit = BrandController.class.getMethod("update", Long.class,
                BrandConfigSaveRequest.class).getAnnotation(RequirePermission.class);
        RequirePermission delete = BrandController.class.getMethod("delete", Long.class)
                .getAnnotation(RequirePermission.class);

        assertThat(list.value()).isEqualTo("sys:brand:list");
        assertThat(add.value()).isEqualTo("sys:brand:add");
        assertThat(edit.value()).isEqualTo("sys:brand:edit");
        assertThat(delete.value()).isEqualTo("sys:brand:delete");

        AuditLog audit = BrandController.class.getMethod("create", BrandConfigSaveRequest.class)
                .getAnnotation(AuditLog.class);
        assertThat(audit).isNotNull();
        assertThat(audit.targetType()).isEqualTo("sys_brand_config");
    }
}
