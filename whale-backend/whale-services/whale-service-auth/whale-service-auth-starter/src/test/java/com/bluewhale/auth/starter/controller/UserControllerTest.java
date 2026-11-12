package com.bluewhale.auth.starter.controller;

import com.bluewhale.auth.application.service.TenantMembershipAppService;
import com.bluewhale.auth.application.service.UserAppService;
import com.bluewhale.auth.application.model.command.AssignUserRolesCommand;
import com.bluewhale.auth.application.model.command.ChangeUserStatusCommand;
import com.bluewhale.auth.application.model.command.CreateUserCommand;
import com.bluewhale.auth.application.model.command.UpdateUserCommand;
import com.bluewhale.auth.application.model.query.UserPageQuery;
import com.bluewhale.auth.application.service.UserPermissionAppService;
import com.bluewhale.auth.application.model.command.ReplaceUserAttrsCommand;
import com.bluewhale.auth.interfaces.dto.request.AssignRolesRequest;
import com.bluewhale.auth.interfaces.dto.request.UpdateAttrsRequest;
import com.bluewhale.auth.interfaces.dto.request.UserCreateRequest;
import com.bluewhale.auth.interfaces.dto.request.UserStatusRequest;
import com.bluewhale.auth.interfaces.dto.request.UserUpdateRequest;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.auth.application.model.vo.ResetPasswordVO;
import com.bluewhale.auth.application.model.vo.UserAttrVO;
import com.bluewhale.auth.application.model.vo.UserVO;
import com.bluewhale.auth.starter.assemble.UserAssembler;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.handler.GlobalExceptionHandler;
import com.bluewhale.common.core.query.PageQuery;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.idempotent.annotation.Idempotent;
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

    private final UserAppService userAppService = mock(UserAppService.class);
    private final UserPermissionAppService userPermissionAppService = mock(UserPermissionAppService.class);
    private final TenantMembershipAppService tenantMembershipAppService = mock(TenantMembershipAppService.class);
    private final UserController controller = new UserController(userAppService, userPermissionAppService,
            tenantMembershipAppService, Mappers.getMapper(UserAssembler.class));

    private MockMvc mockMvc;

    /**
     * 构建 standalone MockMvc：注册全局异常处理器与校验器，使校验失败返回统一响应体
     */
    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        ApiVersionStrategy versionStrategy = new DefaultApiVersionStrategy(
                List.of(new PathApiVersionResolver(1, requestPath -> true)),
                new SemanticApiVersionParser(), false, null, true, null, null);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setApiVersionStrategy(versionStrategy)
                .build();
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
    @DisplayName("GET /user/v1/page：返回 code=0 与分页数据形状")
    void pageShouldReturnPageResult() throws Exception {
        when(userAppService.page(new UserPageQuery(2L, 5L, "alice")))
                .thenReturn(PageResult.of(List.of(userVO()), 1L, 2L, 5L));

        mockMvc.perform(get("/user/v1/page").param("current", "2").param("size", "5").param("keyword", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.total").value(1L))
                .andExpect(jsonPath("$.data.size").value(5L))
                .andExpect(jsonPath("$.data.records[0].username").value("alice"));

        verify(userAppService).page(new UserPageQuery(2L, 5L, "alice"));
    }

    @Test
    @DisplayName("GET /user/v1/{id}：返回 code=0 与用户详情")
    void detailShouldReturnUser() throws Exception {
        when(userAppService.getDetail(USER_ID)).thenReturn(userVO());

        mockMvc.perform(get("/user/v1/{id}", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.username").value("alice"));

        verify(userAppService).getDetail(USER_ID);
    }

    @Test
    @DisplayName("POST /user/v1：合法请求创建成功并返回 code=0")
    void createShouldReturnSuccess() throws Exception {
        when(userAppService.create(any(CreateUserCommand.class))).thenReturn(userVO());

        mockMvc.perform(post("/user/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"secret1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.id").value(USER_ID));

        verify(userAppService).create(any(CreateUserCommand.class));
    }

    @Test
    @DisplayName("PUT /user/v1/{id}：合法请求更新成功并返回 code=0")
    void updateShouldReturnSuccess() throws Exception {
        when(userAppService.update(eq(USER_ID), any(UpdateUserCommand.class))).thenReturn(userVO());

        mockMvc.perform(put("/user/v1/{id}", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"Alice\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()));

        verify(userAppService).update(eq(USER_ID), any(UpdateUserCommand.class));
    }

    @Test
    @DisplayName("DELETE /user/v1/{id}：删除成功并返回 code=0")
    void deleteShouldReturnSuccess() throws Exception {
        mockMvc.perform(delete("/user/v1/{id}", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()));

        verify(userAppService).delete(USER_ID);
    }

    @Test
    @DisplayName("PUT /user/v1/{id}/password：重置口令返回一次性临时口令（REQ-SEC-023）")
    void resetPasswordShouldReturnTemporaryPassword() throws Exception {
        when(userAppService.resetPassword(USER_ID))
                .thenReturn(new ResetPasswordVO(USER_ID, "alice", "Tmp#5Ghz9!Kl"));

        mockMvc.perform(put("/user/v1/{id}/password", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.userId").value(USER_ID))
                .andExpect(jsonPath("$.data.temporaryPassword").value("Tmp#5Ghz9!Kl"))
                .andExpect(jsonPath("$.data.mustChangePassword").value(true));

        verify(userAppService).resetPassword(USER_ID);
    }

    @Test
    @DisplayName("PUT /user/v1/{id}/status：启停用户成功并返回 code=0")
    void changeStatusShouldReturnSuccess() throws Exception {
        mockMvc.perform(put("/user/v1/{id}/status", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()));

        verify(userAppService).changeStatus(eq(USER_ID), any(ChangeUserStatusCommand.class));
    }

    @Test
    @DisplayName("POST /user/v1/{id}/roles：全量覆盖分配角色成功并返回 code=0")
    void assignRolesShouldReturnSuccess() throws Exception {
        mockMvc.perform(post("/user/v1/{id}/roles", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleIds\":[1,2]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()));

        verify(userAppService).assignRoles(eq(USER_ID), eq(new AssignUserRolesCommand(List.of(1L, 2L))));
    }

    @Test
    @DisplayName("GET /user/v1/{id}/roles：返回已分配角色 ID 列表")
    void roleIdsShouldReturnIds() throws Exception {
        when(userAppService.listRoleIds(USER_ID)).thenReturn(List.of(1L, 2L));

        mockMvc.perform(get("/user/v1/{id}/roles", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data[0]").value(1L))
                .andExpect(jsonPath("$.data[1]").value(2L));

        verify(userAppService).listRoleIds(USER_ID);
    }

    @Test
    @DisplayName("GET /user/v1/{id}/attrs：返回用户 ABAC 属性列表")
    void attrsShouldReturnList() throws Exception {
        when(userPermissionAppService.listByUserId(USER_ID)).thenReturn(List.of(userAttrVO()));

        mockMvc.perform(get("/user/v1/{id}/attrs", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data[0].attrKey").value("dept"));

        verify(userPermissionAppService).listByUserId(USER_ID);
    }

    @Test
    @DisplayName("PUT /user/v1/{id}/attrs：全量覆盖写入属性成功并返回 code=0")
    void updateAttrsShouldReturnSuccess() throws Exception {
        mockMvc.perform(put("/user/v1/{id}/attrs", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attrs\":[{\"permissionId\":1,\"attrKey\":\"dept\",\"attrValue\":\"10\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()));

        verify(userPermissionAppService).replaceUserAttrs(eq(USER_ID), any(ReplaceUserAttrsCommand.class));
    }

    @Test
    @DisplayName("POST /user/v1：用户名为空触发校验失败，返回统一参数错误体且不调用服务")
    void createShouldRejectBlankUsername() throws Exception {
        mockMvc.perform(post("/user/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"secret1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(PARAM_ERROR_CODE))
                .andExpect(jsonPath("$.message").value("用户名不能为空"));

        verify(userAppService, never()).create(any());
    }

    @Test
    @DisplayName("权限契约：各端点 @RequirePermission / @AuditLog / @Idempotent 均就位")
    void endpointsShouldDeclarePermissionAndAuditContract() throws Exception {
        assertPermission("page", "sys:user:list", PageQuery.class, String.class);
        assertPermission("detail", "sys:user:list", Long.class);
        assertPermission("create", "sys:user:add", UserCreateRequest.class);
        assertPermission("update", "sys:user:edit", Long.class, UserUpdateRequest.class);
        assertPermission("delete", "sys:user:delete", Long.class);
        assertPermission("resetPassword", "sys:user:resetpwd", Long.class);
        assertPermission("changeStatus", "sys:user:status", Long.class, UserStatusRequest.class);
        assertPermission("assignRoles", "sys:user:assign", Long.class, AssignRolesRequest.class);
        assertPermission("roleIds", "sys:user:list", Long.class);
        assertPermission("attrs", "sys:user:list", Long.class);
        assertPermission("updateAttrs", "sys:user:edit", Long.class, UpdateAttrsRequest.class);

        assertAuditLog("create", UserCreateRequest.class);
        assertAuditLog("update", Long.class, UserUpdateRequest.class);
        assertAuditLog("delete", Long.class);
        assertAuditLog("resetPassword", Long.class);
        assertAuditLog("changeStatus", Long.class, UserStatusRequest.class);
        assertAuditLog("assignRoles", Long.class, AssignRolesRequest.class);
        assertAuditLog("updateAttrs", Long.class, UpdateAttrsRequest.class);

        Idempotent idempotent = UserController.class.getMethod("create", UserCreateRequest.class)
                .getAnnotation(Idempotent.class);
        assertThat(idempotent).as("create 缺少 @Idempotent").isNotNull();
        assertThat(idempotent.biz()).isEqualTo("auth:user:create");

        AuditLog resetPassword = UserController.class
                .getMethod("resetPassword", Long.class).getAnnotation(AuditLog.class);
        assertThat(resetPassword).as("resetPassword 需关闭请求参数记录").isNotNull();
        assertThat(resetPassword.recordParams()).isFalse();
    }
}
