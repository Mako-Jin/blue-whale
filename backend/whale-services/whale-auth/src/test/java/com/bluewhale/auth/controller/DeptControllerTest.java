package com.bluewhale.auth.controller;

import com.bluewhale.auth.application.dept.DeptAppService;
import com.bluewhale.auth.config.DddMigrationProperties;
import com.bluewhale.auth.dto.DeptRequest;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.auth.service.SysDeptService;
import com.bluewhale.auth.vo.DeptVO;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
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

    private final SysDeptService deptService = mock(SysDeptService.class);
    private final DeptAppService deptAppService = mock(DeptAppService.class);
    private final DddMigrationProperties ddd = new DddMigrationProperties();
    private final DeptController controller = new DeptController(deptService, deptAppService, ddd);

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
     * 每个用例结束后复位灰度开关，避免测试类共享的配置实例串扰
     */
    @AfterEach
    void resetDddFlag() {
        ddd.setDept(false);
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
    @DisplayName("GET /dept/tree：返回 code=0 与部门树")
    void treeShouldReturnDeptTree() throws Exception {
        when(deptService.tree()).thenReturn(List.of(deptVO()));

        mockMvc.perform(get("/dept/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data[0].id").value(DEPT_ID))
                .andExpect(jsonPath("$.data[0].deptName").value("研发中心"));

        verify(deptService).tree();
    }

    @Test
    @DisplayName("GET /dept/{id}：返回 code=0 与部门详情")
    void detailShouldReturnDept() throws Exception {
        when(deptService.getDetail(DEPT_ID)).thenReturn(deptVO());

        mockMvc.perform(get("/dept/{id}", DEPT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.deptName").value("研发中心"));

        verify(deptService).getDetail(DEPT_ID);
    }

    @Test
    @DisplayName("POST /dept：合法请求创建成功并返回 code=0")
    void createShouldReturnSuccess() throws Exception {
        mockMvc.perform(post("/dept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deptName\":\"研发中心\",\"parentId\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()));

        verify(deptService).create(any(DeptRequest.class));
    }

    @Test
    @DisplayName("PUT /dept/{id}：合法请求更新成功并返回 code=0")
    void updateShouldReturnSuccess() throws Exception {
        mockMvc.perform(put("/dept/{id}", DEPT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deptName\":\"研发中心\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()));

        verify(deptService).update(eq(DEPT_ID), any(DeptRequest.class));
    }

    @Test
    @DisplayName("DELETE /dept/{id}：删除成功并返回 code=0")
    void deleteShouldReturnSuccess() throws Exception {
        mockMvc.perform(delete("/dept/{id}", DEPT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()));

        verify(deptService).delete(DEPT_ID);
    }

    @Test
    @DisplayName("POST /dept：部门名称空白触发校验失败，返回统一参数错误体且不调用服务")
    void createShouldRejectBlankDeptName() throws Exception {
        mockMvc.perform(post("/dept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deptName\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(PARAM_ERROR_CODE))
                .andExpect(jsonPath("$.message").value("部门名称不能为空"));

        verify(deptService, never()).create(any());
    }

    @Test
    @DisplayName("分流：dept=false 时 GET /dept/{id} 走旧 SysDeptService")
    void detailShouldUseLegacyServiceWhenDeptFlagDisabled() throws Exception {
        when(deptService.getDetail(DEPT_ID)).thenReturn(deptVO());

        mockMvc.perform(get("/dept/{id}", DEPT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()));

        verify(deptService).getDetail(DEPT_ID);
        verify(deptAppService, never()).getDetail(DEPT_ID);
    }

    @Test
    @DisplayName("分流：dept=true 时 GET /dept/{id} 走 DeptAppService")
    void detailShouldUseAppServiceWhenDeptFlagEnabled() throws Exception {
        ddd.setDept(true);
        when(deptAppService.getDetail(DEPT_ID)).thenReturn(deptVO());

        mockMvc.perform(get("/dept/{id}", DEPT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()));

        verify(deptAppService).getDetail(DEPT_ID);
        verify(deptService, never()).getDetail(DEPT_ID);
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
