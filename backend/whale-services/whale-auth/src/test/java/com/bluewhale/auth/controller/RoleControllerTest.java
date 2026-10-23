package com.bluewhale.auth.controller;

import com.bluewhale.auth.application.role.RoleAppService;
import com.bluewhale.auth.config.DddMigrationProperties;
import com.bluewhale.auth.dto.AssignPermissionsRequest;
import com.bluewhale.auth.dto.RoleCreateRequest;
import com.bluewhale.auth.dto.RoleUpdateRequest;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.auth.service.SysRoleService;
import com.bluewhale.auth.vo.RoleVO;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.handler.GlobalExceptionHandler;
import com.bluewhale.common.core.query.PageQuery;
import com.bluewhale.common.core.result.PageResult;
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
 * {@link RoleController} 控制器层单元测试（standalone MockMvc）。
 *
 * <p>覆盖：每个端点的 happy path（{@code code=0} 与预期数据形状）、请求体校验失败
 * （项目统一参数错误体 {@code code=400}）、以及各端点权限/审计注解反射契约。</p>
 *
 * @author BlueWhale
 */
class RoleControllerTest {

    private static final long ROLE_ID = 31L;
    private static final int PARAM_ERROR_CODE = 400;

    private final SysRoleService roleService = mock(SysRoleService.class);
    private final RoleAppService roleAppService = mock(RoleAppService.class);
    private final DddMigrationProperties ddd = new DddMigrationProperties();
    private final RoleController controller = new RoleController(roleService, roleAppService, ddd);

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
        ddd.setRole(false);
    }

    /**
     * 构造角色视图对象
     *
     * @return 角色视图对象
     */
    private static RoleVO roleVO() {
        RoleVO vo = new RoleVO();
        vo.setId(ROLE_ID);
        vo.setRoleCode("admin");
        vo.setRoleName("管理员");
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
        Method method = RoleController.class.getMethod(methodName, parameterTypes);
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
        AuditLog annotation = RoleController.class.getMethod(methodName, parameterTypes).getAnnotation(AuditLog.class);
        assertThat(annotation).as("@AuditLog 缺失: %s", methodName).isNotNull();
    }

    @Test
    @DisplayName("GET /role/page：返回 code=0 与分页数据形状")
    void pageShouldReturnPageResult() throws Exception {
        when(roleService.page(1L, 10L, null)).thenReturn(PageResult.of(List.of(roleVO()), 1L, 1L, 10L));

        mockMvc.perform(get("/role/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.total").value(1L))
                .andExpect(jsonPath("$.data.records[0].roleCode").value("admin"));

        verify(roleService).page(1L, 10L, null);
    }

    @Test
    @DisplayName("GET /role/{id}：返回 code=0 与角色详情")
    void detailShouldReturnRole() throws Exception {
        when(roleService.getDetail(ROLE_ID)).thenReturn(roleVO());

        mockMvc.perform(get("/role/{id}", ROLE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.roleName").value("管理员"));

        verify(roleService).getDetail(ROLE_ID);
    }

    @Test
    @DisplayName("POST /role：合法请求创建成功并返回 code=0")
    void createShouldReturnSuccess() throws Exception {
        when(roleService.create(any(RoleCreateRequest.class))).thenReturn(roleVO());

        mockMvc.perform(post("/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleCode\":\"admin\",\"roleName\":\"管理员\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.roleCode").value("admin"));

        verify(roleService).create(any(RoleCreateRequest.class));
    }

    @Test
    @DisplayName("PUT /role/{id}：合法请求更新成功并返回 code=0")
    void updateShouldReturnSuccess() throws Exception {
        when(roleService.update(eq(ROLE_ID), any(RoleUpdateRequest.class))).thenReturn(roleVO());

        mockMvc.perform(put("/role/{id}", ROLE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleName\":\"管理员\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.id").value(ROLE_ID));

        verify(roleService).update(eq(ROLE_ID), any(RoleUpdateRequest.class));
    }

    @Test
    @DisplayName("DELETE /role/{id}：删除成功并返回 code=0")
    void deleteShouldReturnSuccess() throws Exception {
        mockMvc.perform(delete("/role/{id}", ROLE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()));

        verify(roleService).delete(ROLE_ID);
    }

    @Test
    @DisplayName("POST /role/{id}/permissions：全量覆盖分配权限成功并返回 code=0")
    void assignPermissionsShouldReturnSuccess() throws Exception {
        mockMvc.perform(post("/role/{id}/permissions", ROLE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionIds\":[1,2]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()));

        verify(roleService).assignPermissions(eq(ROLE_ID), eq(List.of(1L, 2L)));
    }

    @Test
    @DisplayName("GET /role/{id}/permissions：返回已分配权限 ID 列表")
    void permissionIdsShouldReturnIds() throws Exception {
        when(roleService.listPermissionIds(ROLE_ID)).thenReturn(List.of(1L, 2L));

        mockMvc.perform(get("/role/{id}/permissions", ROLE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data[0]").value(1L))
                .andExpect(jsonPath("$.data[1]").value(2L));

        verify(roleService).listPermissionIds(ROLE_ID);
    }

    @Test
    @DisplayName("POST /role：角色编码空白触发校验失败，返回统一参数错误体且不调用服务")
    void createShouldRejectBlankRoleCode() throws Exception {
        mockMvc.perform(post("/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleName\":\"管理员\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(PARAM_ERROR_CODE))
                .andExpect(jsonPath("$.message").value("角色编码不能为空"));

        verify(roleService, never()).create(any());
    }

    @Test
    @DisplayName("分流：role=false 时 GET /role/{id} 走旧 SysRoleService")
    void detailShouldUseLegacyServiceWhenRoleFlagDisabled() throws Exception {
        when(roleService.getDetail(ROLE_ID)).thenReturn(roleVO());

        mockMvc.perform(get("/role/{id}", ROLE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()));

        verify(roleService).getDetail(ROLE_ID);
        verify(roleAppService, never()).getDetail(ROLE_ID);
    }

    @Test
    @DisplayName("分流：role=true 时 GET /role/{id} 走 RoleAppService")
    void detailShouldUseAppServiceWhenRoleFlagEnabled() throws Exception {
        ddd.setRole(true);
        when(roleAppService.getDetail(ROLE_ID)).thenReturn(roleVO());

        mockMvc.perform(get("/role/{id}", ROLE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()));

        verify(roleAppService).getDetail(ROLE_ID);
        verify(roleService, never()).getDetail(ROLE_ID);
    }

    @Test
    @DisplayName("权限契约：各端点 @RequirePermission 与写操作 @AuditLog 均就位")
    void endpointsShouldDeclarePermissionAndAuditContract() throws Exception {
        assertPermission("page", "sys:role:list", PageQuery.class, String.class);
        assertPermission("detail", "sys:role:list", Long.class);
        assertPermission("create", "sys:role:add", RoleCreateRequest.class);
        assertPermission("update", "sys:role:edit", Long.class, RoleUpdateRequest.class);
        assertPermission("delete", "sys:role:delete", Long.class);
        assertPermission("assignPermissions", "sys:role:assign", Long.class, AssignPermissionsRequest.class);
        assertPermission("permissionIds", "sys:role:list", Long.class);

        assertAuditLog("create", RoleCreateRequest.class);
        assertAuditLog("update", Long.class, RoleUpdateRequest.class);
        assertAuditLog("delete", Long.class);
        assertAuditLog("assignPermissions", Long.class, AssignPermissionsRequest.class);
    }
}
