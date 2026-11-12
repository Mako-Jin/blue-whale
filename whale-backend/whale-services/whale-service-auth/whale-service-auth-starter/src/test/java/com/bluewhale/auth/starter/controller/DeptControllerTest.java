package com.bluewhale.auth.starter.controller;

import com.bluewhale.auth.application.service.DeptAppService;
import com.bluewhale.auth.application.model.command.SaveDeptCommand;
import com.bluewhale.auth.interfaces.dto.request.DeptRequest;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.auth.application.model.vo.DeptVO;
import com.bluewhale.auth.starter.assemble.DeptAssembler;
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
 * {@link DeptController} 控制器层单元测试（standalone MockMvc）。
 *
 * <p>覆盖：每个端点的 happy path（{@code code=0} 与预期数据形状）、请求体校验失败
 * （项目统一参数错误体 {@code code=400}）、以及各端点权限/审计注解反射契约。</p>
 *
 * @author BlueWhale
 */
class DeptControllerTest {

    private static final long DEPT_ID = 3L;
    private static final int PARAM_ERROR_CODE = 400;

    private final DeptAppService deptAppService = mock(DeptAppService.class);
    private final DeptController controller =
            new DeptController(deptAppService, Mappers.getMapper(DeptAssembler.class));

    private MockMvc mockMvc;

    /**
     * 构建 standalone MockMvc：注册全局异常处理器与校验器，使校验失败返回统一响应体
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
     * 构造部门视图对象
     *
     * @return 部门视图对象
     */
    private static DeptVO deptVO() {
        DeptVO vo = new DeptVO();
        vo.setId(DEPT_ID);
        vo.setParentId(0L);
        vo.setDeptName("研发中心");
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
        Method method = DeptController.class.getMethod(methodName, parameterTypes);
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
        AuditLog annotation = DeptController.class.getMethod(methodName, parameterTypes).getAnnotation(AuditLog.class);
        assertThat(annotation).as("@AuditLog 缺失: %s", methodName).isNotNull();
    }

    @Test
    @DisplayName("GET /dept/v1/tree：返回 code=0 与部门树")
    void treeShouldReturnDeptTree() throws Exception {
        when(deptAppService.tree()).thenReturn(List.of(deptVO()));

        mockMvc.perform(get("/dept/v1/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data[0].id").value(DEPT_ID))
                .andExpect(jsonPath("$.data[0].deptName").value("研发中心"));

        verify(deptAppService).tree();
    }

    @Test
    @DisplayName("GET /dept/v1/{id}：返回 code=0 与部门详情")
    void detailShouldReturnDept() throws Exception {
        when(deptAppService.getDetail(DEPT_ID)).thenReturn(deptVO());

        mockMvc.perform(get("/dept/v1/{id}", DEPT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.deptName").value("研发中心"));

        verify(deptAppService).getDetail(DEPT_ID);
    }

    @Test
    @DisplayName("POST /dept/v1：合法请求创建成功并返回 code=0")
    void createShouldReturnSuccess() throws Exception {
        mockMvc.perform(post("/dept/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deptName\":\"研发中心\",\"parentId\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()));

        verify(deptAppService).create(any(SaveDeptCommand.class));
    }

    @Test
    @DisplayName("PUT /dept/v1/{id}：合法请求更新成功并返回 code=0")
    void updateShouldReturnSuccess() throws Exception {
        mockMvc.perform(put("/dept/v1/{id}", DEPT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deptName\":\"研发中心\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()));

        verify(deptAppService).update(eq(DEPT_ID), any(SaveDeptCommand.class));
    }

    @Test
    @DisplayName("DELETE /dept/v1/{id}：删除成功并返回 code=0")
    void deleteShouldReturnSuccess() throws Exception {
        mockMvc.perform(delete("/dept/v1/{id}", DEPT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()));

        verify(deptAppService).delete(DEPT_ID);
    }

    @Test
    @DisplayName("POST /dept/v1：部门名称空白触发校验失败，返回统一参数错误体且不调用服务")
    void createShouldRejectBlankDeptName() throws Exception {
        mockMvc.perform(post("/dept/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deptName\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(PARAM_ERROR_CODE))
                .andExpect(jsonPath("$.message").value("部门名称不能为空"));

        verify(deptAppService, never()).create(any());
    }

    @Test
    @DisplayName("权限契约：各端点 @RequirePermission 与写操作 @AuditLog 均就位")
    void endpointsShouldDeclarePermissionAndAuditContract() throws Exception {
        assertPermission("tree", "sys:dept:list");
        assertPermission("detail", "sys:dept:list", Long.class);
        assertPermission("create", "sys:dept:add", DeptRequest.class);
        assertPermission("update", "sys:dept:edit", Long.class, DeptRequest.class);
        assertPermission("delete", "sys:dept:delete", Long.class);

        assertAuditLog("create", DeptRequest.class);
        assertAuditLog("update", Long.class, DeptRequest.class);
        assertAuditLog("delete", Long.class);
    }
}
