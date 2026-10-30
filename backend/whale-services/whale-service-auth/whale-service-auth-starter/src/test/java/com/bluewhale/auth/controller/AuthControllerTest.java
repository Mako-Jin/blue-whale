package com.bluewhale.auth.controller;

import com.bluewhale.auth.dto.LoginRequest;
import com.bluewhale.auth.dto.LogoutRequest;
import com.bluewhale.auth.dto.RefreshRequest;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.auth.service.AuthService;
import com.bluewhale.auth.vo.AuthMeResponse;
import com.bluewhale.auth.vo.LoginResponse;
import com.bluewhale.auth.vo.MenuVO;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;

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
 * {@link AuthController} 控制器层单元测试（standalone MockMvc）。
 *
 * <p>覆盖：登录 / 刷新 / 当前用户 / 登出四个端点的 happy path（{@code code=0} 与预期数据形状）、
 * 请求体校验失败（项目统一参数错误体 {@code code=400}）、{@code /auth/me} 的
 * {@code @RequirePermission} 与凭据端点 {@code @AuditLog} 契约反射校验。</p>
 *
 * @author BlueWhale
 */
class AuthControllerTest {

    private static final String ACCESS_TOKEN = "access-token";
    private static final String REFRESH_TOKEN = "refresh-token";
    private static final String BEARER_ACCESS = "Bearer " + ACCESS_TOKEN;
    private static final long EXPIRES_IN = 7200L;
    private static final long USER_ID = 51L;
    private static final int PARAM_ERROR_CODE = 400;

    private final AuthService authService = mock(AuthService.class);
    private final AuthController controller = new AuthController(authService);

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
     * 构造登录响应
     *
     * @return 登录响应（含双令牌与用户基础信息）
     */
    private static LoginResponse loginResponse() {
        LoginResponse response = new LoginResponse();
        response.setAccessToken(ACCESS_TOKEN);
        response.setRefreshToken(REFRESH_TOKEN);
        response.setTokenType("Bearer");
        response.setExpiresIn(EXPIRES_IN);
        response.setUsername("alice");
        response.setNickname("Alice");
        response.setRoles(List.of("admin"));
        response.setMustChangePassword(true);
        return response;
    }

    /**
     * 构造当前登录用户响应
     *
     * @return 当前登录用户响应
     */
    private static AuthMeResponse authMeResponse() {
        MenuVO menu = new MenuVO();
        menu.setId(1L);
        menu.setName("系统管理");
        AuthMeResponse response = new AuthMeResponse();
        response.setUserId(USER_ID);
        response.setUsername("alice");
        response.setNickname("Alice");
        response.setRoles(List.of("admin"));
        response.setPermissions(List.of("sys:user:list"));
        response.setMenus(List.of(menu));
        return response;
    }

    @Test
    @DisplayName("POST /auth/login：返回 code=0 与双令牌登录响应")
    void loginShouldReturnTokens() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenReturn(loginResponse());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"secret1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.accessToken").value(ACCESS_TOKEN))
                .andExpect(jsonPath("$.data.refreshToken").value(REFRESH_TOKEN))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(EXPIRES_IN))
                .andExpect(jsonPath("$.data.roles[0]").value("admin"))
                .andExpect(jsonPath("$.data.mustChangePassword").value(true));

        verify(authService).login(any(LoginRequest.class));
    }

    @Test
    @DisplayName("POST /auth/refresh：返回 code=0 与新发令牌")
    void refreshShouldReturnNewTokens() throws Exception {
        when(authService.refresh(any(RefreshRequest.class))).thenReturn(loginResponse());

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + REFRESH_TOKEN + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.accessToken").value(ACCESS_TOKEN));

        verify(authService).refresh(any(RefreshRequest.class));
    }

    @Test
    @DisplayName("GET /auth/me：返回 code=0 与角色/权限码/菜单树")
    void meShouldReturnCurrentUser() throws Exception {
        when(authService.me()).thenReturn(authMeResponse());

        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.userId").value(USER_ID))
                .andExpect(jsonPath("$.data.permissions[0]").value("sys:user:list"))
                .andExpect(jsonPath("$.data.menus[0].name").value("系统管理"));

        verify(authService).me();
    }

    @Test
    @DisplayName("POST /auth/logout：双令牌失效成功并返回 code=0")
    void logoutShouldReturnSuccess() throws Exception {
        mockMvc.perform(post("/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, BEARER_ACCESS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + REFRESH_TOKEN + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()));

        verify(authService).logout(BEARER_ACCESS, REFRESH_TOKEN);
    }

    @Test
    @DisplayName("POST /auth/logout：未携带请求体时 refreshToken 透传 null")
    void logoutShouldPassNullWhenBodyAbsent() throws Exception {
        mockMvc.perform(post("/auth/logout").header(HttpHeaders.AUTHORIZATION, BEARER_ACCESS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()));

        verify(authService).logout(BEARER_ACCESS, null);
    }

    @Test
    @DisplayName("POST /auth/login：用户名为空触发校验失败，返回统一参数错误体且不调用服务")
    void loginShouldRejectBlankUsername() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"secret1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(PARAM_ERROR_CODE))
                .andExpect(jsonPath("$.message").value("用户名不能为空"));

        verify(authService, never()).login(any());
    }

    @Test
    @DisplayName("POST /auth/refresh：refreshToken 空白触发校验失败，返回统一参数错误体且不调用服务")
    void refreshShouldRejectBlankRefreshToken() throws Exception {
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(PARAM_ERROR_CODE))
                .andExpect(jsonPath("$.message").value("refreshToken 不能为空"));

        verify(authService, never()).refresh(any());
    }

    @Test
    @DisplayName("/auth/me 权限契约：声明 @RequirePermission（仅需登录，无具体权限点）")
    void meShouldDeclareRequirePermission() throws Exception {
        RequirePermission annotation = AuthController.class.getMethod("me").getAnnotation(RequirePermission.class);

        assertThat(annotation).as("/auth/me 缺少 @RequirePermission").isNotNull();
        assertThat(annotation.value()).isEmpty();
        assertThat(annotation.requireLogin()).isTrue();
    }

    @Test
    @DisplayName("凭据端点审计契约：login/logout 声明 @AuditLog 且 recordParams=false（凭据不落库）")
    void credentialEndpointsShouldNotRecordParams() throws Exception {
        AuditLog login = AuthController.class.getMethod("login", LoginRequest.class).getAnnotation(AuditLog.class);
        assertThat(login).as("/auth/login 缺少 @AuditLog").isNotNull();
        assertThat(login.operation()).isEqualTo("用户登录");
        assertThat(login.recordParams()).isFalse();

        AuditLog logout = AuthController.class.getMethod("logout", String.class, LogoutRequest.class)
                .getAnnotation(AuditLog.class);
        assertThat(logout).as("/auth/logout 缺少 @AuditLog").isNotNull();
        assertThat(logout.recordParams()).isFalse();
    }
}
