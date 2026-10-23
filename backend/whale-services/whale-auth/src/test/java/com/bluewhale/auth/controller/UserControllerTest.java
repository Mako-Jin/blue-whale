package com.bluewhale.auth.controller;

import com.bluewhale.auth.application.user.UserAppService;
import com.bluewhale.auth.application.userpermission.UserPermissionAppService;
import com.bluewhale.auth.config.DddMigrationProperties;
import com.bluewhale.auth.dto.AssignRolesRequest;
import com.bluewhale.auth.dto.ChangePasswordRequest;
import com.bluewhale.auth.dto.UpdateAttrsRequest;
import com.bluewhale.auth.dto.UserCreateRequest;
import com.bluewhale.auth.dto.UserStatusRequest;
import com.bluewhale.auth.dto.UserUpdateRequest;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.auth.service.SysUserPermissionService;
import com.bluewhale.auth.service.SysUserService;
import com.bluewhale.auth.vo.UserAttrVO;
import com.bluewhale.auth.vo.UserVO;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.handler.GlobalExceptionHandler;
import com.bluewhale.common.core.query.PageQuery;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.idempotent.annotation.Idempotent;
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
 * {@link UserController} 控制器层单元测试（standalone MockMvc）。
 *
 * <p>覆盖：每个端点的 happy path（{@code code=0} 与预期数据形状）、创建用户请求体校验失败
 * （项目统一参数错误体 {@code code=400}）、以及各端点权限/审计/幂等注解反射契约。</p>
 *
 * @author BlueWhale
 */
class UserControllerTest {

    private static final long USER_ID = 41L;
    private static final int PARAM_ERROR_CODE = 400;

    private final SysUserService userService = mock(SysUserService.class);
    private final SysUserPermissionService userPermissionService = mock(SysUserPermissionService.class);
    private final UserAppService userAppService = mock(UserAppService.class);
    private final UserPermissionAppService userPermissionAppService = mock(UserPermissionAppService.class);
    private final DddMigrationProperties ddd = new DddMigrationProperties();
    private final UserController controller = new UserController(
            userService, userPermissionService, userAppService, userPermissionAppService, ddd);

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
        ddd.setUser(false);
    }

    /**
     * 构造用户视图对象
     *
     * @return 用户视图对象
     */
    private static UserVO userVO() {
        UserVO vo = new UserVO();
        vo.setId(USER_ID);
        vo.setUsername("alice");
        vo.setNickname("Alice");
        vo.setStatus(1);
        return vo;
    }

    /**
     * 构造用户 ABAC 属性视图对象
     *
     * @return 用户属性视图对象
     */
    private static UserAttrVO userAttrVO() {
        UserAttrVO vo = new UserAttrVO();
        vo.setUserId(USER_ID);
        vo.setAttrKey("dept");
        vo.setAttrValue("10");
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
        Method method = UserController.class.getMethod(methodName, parameterTypes);
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
        AuditLog annotation = UserController.class.getMethod(methodName, parameterTypes).getAnnotation(AuditLog.class);
        assertThat(annotation).as("@AuditLog 缺失: %s", methodName).isNotNull();
    }

    @Test
    @DisplayName("GET /user/page：返回 code=0 与分页数据形状")
    void pageShouldReturnPageResult() throws Exception {
        when(userService.page(2L, 5L, "alice")).thenReturn(PageResult.of(List.of(userVO()), 1L, 2L, 5L));

        mockMvc.perform(get("/user/page").param("current", "2").param("size", "5").param("keyword", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.total").value(1L))
                .andExpect(jsonPath("$.data.size").value(5L))
                .andExpect(jsonPath("$.data.records[0].username").value("alice"));

        verify(userService).page(2L, 5L, "alice");
    }

    @Test
    @DisplayName("GET /user/{id}：返回 code=0 与用户详情")
    void detailShouldReturnUser() throws Exception {
        when(userService.getDetail(USER_ID)).thenReturn(userVO());

        mockMvc.perform(get("/user/{id}", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.username").value("alice"));

        verify(userService).getDetail(USER_ID);
    }

    @Test
    @DisplayName("POST /user：合法请求创建成功并返回 code=0")
    void createShouldReturnSuccess() throws Exception {
        when(userService.create(any(UserCreateRequest.class))).thenReturn(userVO());

        mockMvc.perform(post("/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"secret1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.id").value(USER_ID));

        verify(userService).create(any(UserCreateRequest.class));
    }

    @Test
    @DisplayName("PUT /user/{id}：合法请求更新成功并返回 code=0")
    void updateShouldReturnSuccess() throws Exception {
        when(userService.update(eq(USER_ID), any(UserUpdateRequest.class))).thenReturn(userVO());

        mockMvc.perform(put("/user/{id}", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"Alice\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()));

        verify(userService).update(eq(USER_ID), any(UserUpdateRequest.class));
    }

    @Test
    @DisplayName("DELETE /user/{id}：删除成功并返回 code=0")
    void deleteShouldReturnSuccess() throws Exception {
        mockMvc.perform(delete("/user/{id}", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()));

        verify(userService).delete(USER_ID);
    }

    @Test
    @DisplayName("PUT /user/{id}/password：重置密码成功并返回 code=0")
    void changePasswordShouldReturnSuccess() throws Exception {
        mockMvc.perform(put("/user/{id}/password", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"secret1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()));

        verify(userService).changePassword(eq(USER_ID), any(ChangePasswordRequest.class));
    }

    @Test
    @DisplayName("PUT /user/{id}/status：启停用户成功并返回 code=0")
    void changeStatusShouldReturnSuccess() throws Exception {
        mockMvc.perform(put("/user/{id}/status", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()));

        verify(userService).changeStatus(eq(USER_ID), any(UserStatusRequest.class));
    }

    @Test
    @DisplayName("POST /user/{id}/roles：全量覆盖分配角色成功并返回 code=0")
    void assignRolesShouldReturnSuccess() throws Exception {
        mockMvc.perform(post("/user/{id}/roles", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleIds\":[1,2]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()));

        verify(userService).assignRoles(eq(USER_ID), eq(List.of(1L, 2L)));
    }

    @Test
    @DisplayName("GET /user/{id}/roles：返回已分配角色 ID 列表")
    void roleIdsShouldReturnIds() throws Exception {
        when(userService.listRoleIds(USER_ID)).thenReturn(List.of(1L, 2L));

        mockMvc.perform(get("/user/{id}/roles", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data[0]").value(1L))
                .andExpect(jsonPath("$.data[1]").value(2L));

        verify(userService).listRoleIds(USER_ID);
    }

    @Test
    @DisplayName("GET /user/{id}/attrs：返回用户 ABAC 属性列表")
    void attrsShouldReturnList() throws Exception {
        when(userPermissionService.listByUserId(USER_ID)).thenReturn(List.of(userAttrVO()));

        mockMvc.perform(get("/user/{id}/attrs", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data[0].attrKey").value("dept"));

        verify(userPermissionService).listByUserId(USER_ID);
    }

    @Test
    @DisplayName("PUT /user/{id}/attrs：全量覆盖写入属性成功并返回 code=0")
    void updateAttrsShouldReturnSuccess() throws Exception {
        mockMvc.perform(put("/user/{id}/attrs", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attrs\":[{\"permissionId\":1,\"attrKey\":\"dept\",\"attrValue\":\"10\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()));

        verify(userPermissionService).replaceUserAttrs(eq(USER_ID), any(UpdateAttrsRequest.class));
    }

    @Test
    @DisplayName("POST /user：用户名为空触发校验失败，返回统一参数错误体且不调用服务")
    void createShouldRejectBlankUsername() throws Exception {
        mockMvc.perform(post("/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"secret1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(PARAM_ERROR_CODE))
                .andExpect(jsonPath("$.message").value("用户名不能为空"));

        verify(userService, never()).create(any());
    }

    @Test
    @DisplayName("分流：user=false 时 GET /user/{id} 走旧 SysUserService")
    void detailShouldUseLegacyServiceWhenUserFlagDisabled() throws Exception {
        when(userService.getDetail(USER_ID)).thenReturn(userVO());

        mockMvc.perform(get("/user/{id}", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()));

        verify(userService).getDetail(USER_ID);
        verify(userAppService, never()).getDetail(USER_ID);
    }

    @Test
    @DisplayName("分流：user=true 时 GET /user/{id} 走 UserAppService")
    void detailShouldUseAppServiceWhenUserFlagEnabled() throws Exception {
        ddd.setUser(true);
        when(userAppService.getDetail(USER_ID)).thenReturn(userVO());

        mockMvc.perform(get("/user/{id}", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()));

        verify(userAppService).getDetail(USER_ID);
        verify(userService, never()).getDetail(USER_ID);
    }

    @Test
    @DisplayName("分流：user=true 时 GET /user/{id}/attrs 走 UserPermissionAppService")
    void attrsShouldUseAppServiceWhenUserFlagEnabled() throws Exception {
        ddd.setUser(true);
        when(userPermissionAppService.listByUserId(USER_ID)).thenReturn(List.of(userAttrVO()));

        mockMvc.perform(get("/user/{id}/attrs", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data[0].attrKey").value("dept"));

        verify(userPermissionAppService).listByUserId(USER_ID);
        verify(userPermissionService, never()).listByUserId(USER_ID);
    }

    @Test
    @DisplayName("权限契约：各端点 @RequirePermission / @AuditLog / @Idempotent 均就位")
    void endpointsShouldDeclarePermissionAndAuditContract() throws Exception {
        assertPermission("page", "sys:user:list", PageQuery.class, String.class);
        assertPermission("detail", "sys:user:list", Long.class);
        assertPermission("create", "sys:user:add", UserCreateRequest.class);
        assertPermission("update", "sys:user:edit", Long.class, UserUpdateRequest.class);
        assertPermission("delete", "sys:user:delete", Long.class);
        assertPermission("changePassword", "sys:user:resetPwd", Long.class, ChangePasswordRequest.class);
        assertPermission("changeStatus", "sys:user:status", Long.class, UserStatusRequest.class);
        assertPermission("assignRoles", "sys:user:assign", Long.class, AssignRolesRequest.class);
        assertPermission("roleIds", "sys:user:list", Long.class);
        assertPermission("attrs", "sys:user:list", Long.class);
        assertPermission("updateAttrs", "sys:user:edit", Long.class, UpdateAttrsRequest.class);

        assertAuditLog("create", UserCreateRequest.class);
        assertAuditLog("update", Long.class, UserUpdateRequest.class);
        assertAuditLog("delete", Long.class);
        assertAuditLog("changePassword", Long.class, ChangePasswordRequest.class);
        assertAuditLog("changeStatus", Long.class, UserStatusRequest.class);
        assertAuditLog("assignRoles", Long.class, AssignRolesRequest.class);
        assertAuditLog("updateAttrs", Long.class, UpdateAttrsRequest.class);

        Idempotent idempotent = UserController.class.getMethod("create", UserCreateRequest.class)
                .getAnnotation(Idempotent.class);
        assertThat(idempotent).as("create 缺少 @Idempotent").isNotNull();
        assertThat(idempotent.biz()).isEqualTo("auth:user:create");

        AuditLog changePassword = UserController.class
                .getMethod("changePassword", Long.class, ChangePasswordRequest.class).getAnnotation(AuditLog.class);
        assertThat(changePassword).as("changePassword 需关闭请求参数记录").isNotNull();
        assertThat(changePassword.recordParams()).isFalse();
    }
}
