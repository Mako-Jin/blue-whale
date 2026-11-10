package com.bluewhale.auth.interfaces.controller;

import com.bluewhale.auth.application.permission.PermissionAppService;
import com.bluewhale.auth.application.permission.command.SavePermissionCommand;
import com.bluewhale.auth.application.permission.query.PermissionPageQuery;
import com.bluewhale.auth.interfaces.dto.PermissionRequest;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.auth.application.vo.PermissionVO;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.handler.GlobalExceptionHandler;
import com.bluewhale.common.core.query.PageQuery;
import com.bluewhale.common.core.result.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link PermissionController} 控制器层单元测试（standalone MockMvc）。
 *
 * <p>覆盖：每个端点的 happy path（{@code code=0} 与预期数据形状）、请求体校验失败
 * （项目统一参数错误体 {@code code=400}）、以及各端点权限/审计注解反射契约。</p>
 *
 * @author BlueWhale
 */
class PermissionControllerTest {

    private static final long PERMISSION_ID = 21L;
    private static final int PARAM_ERROR_CODE = 400;

    private final PermissionAppService permissionAppService = mock(PermissionAppService.class);
    private final PermissionController controller = new PermissionController(permissionAppService);

    private MockMvc mockMvc;

    /**
     * 构建 standalone MockMvc：注册全局异常处理器与校验器，使校验失败返回统一响应体
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
     * 构造权限点视图对象
     *
     * @return 权限点视图对象
     */
    private static PermissionVO permissionVO() {
        PermissionVO vo = new PermissionVO();
        vo.setId(PERMISSION_ID);
        vo.setPermCode("sys:permission:list");
        vo.setPermName("权限点查询");
        vo.setPermType("API");
        return vo;
    }

    /**
     * 断言控制器方法声明了预期的权限码
     *
     * @param methodName 方法名
     * @param permValue 期望的权限码
     * @param parameterTypes 方法参数类型
     * @throws NoSuchMethodException 方法不存在时抛出
     */
    private static void assertPermission(String methodName, String permValue, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = PermissionController.class.getMethod(methodName, parameterTypes);
        RequirePermission annotation = method.getAnnotation(RequirePermission.class);
        assertThat(annotation).as("@RequirePermission 缺失: %s", methodName).isNotNull();
        assertThat(annotation.value()).as("@RequirePermission value 不符: %s", methodName).isEqualTo(permValue);
    }

    /**
     * 断言控制器方法声明了审计注解
     *
     * @param methodName 方法名
     * @param parameterTypes 方法参数类型
     * @throws NoSuchMethodException 方法不存在时抛出
     */
    private static void assertAuditLog(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        AuditLog annotation = PermissionController.class.getMethod(methodName, parameterTypes)
                .getAnnotation(AuditLog.class);
        assertThat(annotation).as("@AuditLog 缺失: %s", methodName).isNotNull();
    }

    @Test
    @DisplayName("GET /permission/page：返回 code=0 与分页数据形状")
    void pageShouldReturnPageResult() throws Exception {
        when(permissionAppService.page(new PermissionPageQuery(1L, 10L, null, "API")))
                .thenReturn(PageResult.of(List.of(permissionVO()), 1L, 1L, 10L));

        mockMvc.perform(get("/permission/page").param("permType", "API"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.total").value(1L))
                .andExpect(jsonPath("$.data.current").value(1L))
                .andExpect(jsonPath("$.data.records[0].permCode").value("sys:permission:list"));

        verify(permissionAppService).page(new PermissionPageQuery(1L, 10L, null, "API"));
    }

    @Test
    @DisplayName("GET /permission/{id}：返回 code=0 与权限点详情")
    void detailShouldReturnPermission() throws Exception {
        when(permissionAppService.getDetail(PERMISSION_ID)).thenReturn(permissionVO());

        mockMvc.perform(get("/permission/{id}", PERMISSION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.permType").value("API"));

        verify(permissionAppService).getDetail(PERMISSION_ID);
    }

    @Test
    @DisplayName("POST /permission：合法请求创建成功并返回 code=0")
    void createShouldReturnSuccess() throws Exception {
        mockMvc.perform(post("/permission")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permCode\":\"sys:permission:list\",\"permName\":\"权限点查询\",\"permType\":\"API\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()));

        verify(permissionAppService).create(any(SavePermissionCommand.class));
    }

    @Test
    @DisplayName("PUT /permission/{id}：合法请求更新成功并返回 code=0")
    void updateShouldReturnSuccess() throws Exception {
        mockMvc.perform(put("/permission/{id}", PERMISSION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permCode\":\"sys:permission:list\",\"permName\":\"权限点查询\",\"permType\":\"API\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()));

        verify(permissionAppService).update(eq(PERMISSION_ID), any(SavePermissionCommand.class));
    }

    @Test
    @DisplayName("DELETE /permission/{id}：删除成功并返回 code=0")
    void deleteShouldReturnSuccess() throws Exception {
        mockMvc.perform(delete("/permission/{id}", PERMISSION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()));

        verify(permissionAppService).delete(PERMISSION_ID);
    }

    @Test
    @DisplayName("POST /permission：权限类型空白触发校验失败，返回统一参数错误体且不调用服务")
    void createShouldRejectBlankPermType() throws Exception {
        mockMvc.perform(post("/permission")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permCode\":\"sys:permission:list\",\"permName\":\"权限点查询\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(PARAM_ERROR_CODE))
                .andExpect(jsonPath("$.message").value("权限类型不能为空"));

        verify(permissionAppService, never()).create(any());
    }

    @Test
    @DisplayName("权限契约：各端点 @RequirePermission 与写操作 @AuditLog 均就位")
    void endpointsShouldDeclarePermissionAndAuditContract() throws Exception {
        assertPermission("page", "sys:permission:list", PageQuery.class, String.class, String.class);
        assertPermission("detail", "sys:permission:list", Long.class);
        assertPermission("create", "sys:permission:add", PermissionRequest.class);
        assertPermission("update", "sys:permission:edit", Long.class, PermissionRequest.class);
        assertPermission("delete", "sys:permission:delete", Long.class);

        assertAuditLog("create", PermissionRequest.class);
        assertAuditLog("update", Long.class, PermissionRequest.class);
        assertAuditLog("delete", Long.class);
    }
}
