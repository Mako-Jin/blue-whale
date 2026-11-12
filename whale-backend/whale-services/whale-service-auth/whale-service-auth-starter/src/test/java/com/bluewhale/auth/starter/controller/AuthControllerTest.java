package com.bluewhale.auth.starter.controller;

import com.bluewhale.auth.application.model.command.LoginCommand;
import com.bluewhale.auth.application.model.command.LogoutCommand;
import com.bluewhale.auth.application.model.command.RefreshCommand;
import com.bluewhale.auth.application.model.command.SwitchTenantCommand;
import com.bluewhale.auth.application.model.result.CurrentUserResult;
import com.bluewhale.auth.application.model.result.LoginResult;
import com.bluewhale.auth.application.service.SessionAppService;
import com.bluewhale.auth.application.model.command.ChangeOwnPasswordCommand;
import com.bluewhale.auth.application.service.PasswordAppService;
import com.bluewhale.auth.application.model.vo.MenuVO;
import com.bluewhale.auth.interfaces.dto.request.ChangeOwnPasswordRequest;
import com.bluewhale.auth.interfaces.dto.request.LoginRequest;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.auth.starter.assemble.AuthAssembler;
import com.bluewhale.auth.interfaces.web.AuthCookieManager;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.handler.GlobalExceptionHandler;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.accept.ApiVersionStrategy;
import org.springframework.web.accept.DefaultApiVersionStrategy;
import org.springframework.web.accept.PathApiVersionResolver;
import org.springframework.web.accept.SemanticApiVersionParser;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link AuthController} 控制器层单元测试（standalone MockMvc，V-03 Cookie 契约 + Boot 4 版本化路径）。
 *
 * <p>覆盖：登录 / 刷新 / 切换租户 / 登出四个端点的 Cookie 行为（refresh 令牌只进
 * HttpOnly Cookie、CSRF 双提交 403 拦截、登出清除 Cookie）、响应体不含 refreshToken、
 * 请求体校验失败（统一参数错误体 {@code code=400}）、{@code /auth/v1/me} 的
 * {@code @RequirePermission} 与凭据端点 {@code @AuditLog} 契约反射校验。</p>
 *
 * @author BlueWhale
 */
class AuthControllerTest {

    private static final String ACCESS_TOKEN = "access-token";
    private static final String OLD_REFRESH_TOKEN = "old-refresh-token";
    private static final String NEW_REFRESH_TOKEN = "new-refresh-token";
    private static final String BEARER_ACCESS = "Bearer " + ACCESS_TOKEN;
    private static final String CSRF_TOKEN = "csrf-token";
    private static final String TAMPERED_CSRF_TOKEN = "tampered-csrf-token";
    private static final long EXPIRES_IN = 7200L;
    private static final long USER_ID = 51L;
    private static final long COOKIE_MAX_AGE_SECONDS = 86400L;
    private static final int PARAM_ERROR_CODE = 400;
    private static final int FORBIDDEN_CODE = 403;
    private static final int UNAUTHORIZED_CODE = 401;

    private final SessionAppService sessionAppService = mock(SessionAppService.class);

    private final PasswordAppService passwordAppService = mock(PasswordAppService.class);

    private final AuthAssembler authAssembler = Mappers.getMapper(AuthAssembler.class);

    private final AuthController controller = new AuthController(sessionAppService, passwordAppService,
            new AuthCookieManager(false, 1440L), authAssembler);

    private MockMvc mockMvc;

    /**
     * 构建 standalone MockMvc：注册全局异常处理器、校验器与 API 版本策略，
     * 使 {@code /auth/v1/**} 路径可按 Boot 4 路径段版本规则匹配。
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
     * 构造登录结果
     *
     * @param refreshToken 签发的 refreshToken（仅写 Cookie）
     * @return 登录结果（响应体不含 refreshToken）
     */
    private static LoginResult loginResult(String refreshToken) {
        return new LoginResult(ACCESS_TOKEN, "Bearer", EXPIRES_IN, USER_ID, 1L, "alice", "Alice",
                List.of("admin"), true, false, -1L, refreshToken);
    }

    /**
     * 构造当前登录用户结果
     *
     * @return 当前登录用户结果
     */
    private static CurrentUserResult currentUserResult() {
        MenuVO menu = new MenuVO();
        menu.setId(1L);
        menu.setName("系统管理");
        return new CurrentUserResult(1L, USER_ID, "alice", "Alice",
                List.of("admin"), List.of("sys:user:list"), List.of(menu));
    }

    /**
     * 断言响应下发了 refresh / CSRF 两枚 Cookie，且标志符合 V-03 契约（dev 非 Secure）
     *
     * @param result MockMvc 执行结果
     * @param expectedRefresh 期望写入 refresh Cookie 的令牌值
     */
    private static void assertAuthCookies(MvcResult result, String expectedRefresh) {
        List<String> cookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cookies).anySatisfy(cookie -> {
            assertThat(cookie).startsWith(AuthCookieManager.REFRESH_COOKIE_NAME + "=" + expectedRefresh);
            assertThat(cookie).contains("Path=/auth/v1")
                    .contains("HttpOnly")
                    .contains("SameSite=Strict")
                    .contains("Max-Age=" + COOKIE_MAX_AGE_SECONDS)
                    .doesNotContain("Secure");
        });
        assertThat(cookies).anySatisfy(cookie -> {
            assertThat(cookie).matches(AuthCookieManager.CSRF_COOKIE_NAME + "=[0-9a-f]{64};.*");
            assertThat(cookie).contains("Path=/;")
                    .contains("SameSite=Strict")
                    .contains("Max-Age=" + COOKIE_MAX_AGE_SECONDS)
                    .doesNotContain("HttpOnly")
                    .doesNotContain("Secure");
        });
    }

    /**
     * 断言登出响应将两枚认证 Cookie 置为立即过期（Max-Age=0）
     *
     * @param result MockMvc 执行结果
     */
    private static void assertCookiesCleared(MvcResult result) {
        List<String> cookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cookies).anySatisfy(cookie -> {
            assertThat(cookie).startsWith(AuthCookieManager.REFRESH_COOKIE_NAME + "=");
            assertThat(cookie).contains("Max-Age=0").contains("HttpOnly").contains("Path=/auth/v1");
        });
        assertThat(cookies).anySatisfy(cookie -> {
            assertThat(cookie).startsWith(AuthCookieManager.CSRF_COOKIE_NAME + "=");
            assertThat(cookie).contains("Max-Age=0").contains("Path=/;").doesNotContain("HttpOnly");
        });
    }

    @Test
    @DisplayName("POST /auth/v1/login：返回 code=0，refreshToken 只进 Cookie、响应体不可见")
    void loginShouldSetCookiesAndHideRefreshToken() throws Exception {
        when(sessionAppService.login(any())).thenReturn(loginResult(OLD_REFRESH_TOKEN));

        MvcResult result = mockMvc.perform(post("/auth/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"secret1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.accessToken").value(ACCESS_TOKEN))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(EXPIRES_IN))
                .andExpect(jsonPath("$.data.roles[0]").value("admin"))
                .andExpect(jsonPath("$.data.mustChangePassword").value(true))
                .andReturn();

        assertAuthCookies(result, OLD_REFRESH_TOKEN);
        verify(sessionAppService).login(new LoginCommand(null, "alice", "secret1"));
    }

    @Test
    @DisplayName("POST /auth/v1/refresh：凭 Cookie + CSRF 双提交换发，轮换下发新 Cookie")
    void refreshShouldRotateFromCookie() throws Exception {
        when(sessionAppService.refresh(any())).thenReturn(loginResult(NEW_REFRESH_TOKEN));

        MvcResult result = mockMvc.perform(post("/auth/v1/refresh")
                        .cookie(new Cookie(AuthCookieManager.REFRESH_COOKIE_NAME, OLD_REFRESH_TOKEN),
                                new Cookie(AuthCookieManager.CSRF_COOKIE_NAME, CSRF_TOKEN))
                        .header(AuthCookieManager.CSRF_HEADER_NAME, CSRF_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.accessToken").value(ACCESS_TOKEN))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andReturn();

        assertAuthCookies(result, NEW_REFRESH_TOKEN);
        verify(sessionAppService).refresh(new RefreshCommand(OLD_REFRESH_TOKEN));
    }

    @Test
    @DisplayName("POST /auth/v1/refresh：缺失 X-CSRF-Token 头返回 403，且不触发刷新")
    void refreshShouldRejectMissingCsrfHeader() throws Exception {
        mockMvc.perform(post("/auth/v1/refresh")
                        .cookie(new Cookie(AuthCookieManager.REFRESH_COOKIE_NAME, OLD_REFRESH_TOKEN),
                                new Cookie(AuthCookieManager.CSRF_COOKIE_NAME, CSRF_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(FORBIDDEN_CODE));

        verify(sessionAppService, never()).refresh(any());
    }

    @Test
    @DisplayName("POST /auth/v1/refresh：X-CSRF-Token 头与 Cookie 不等返回 403")
    void refreshShouldRejectMismatchedCsrf() throws Exception {
        mockMvc.perform(post("/auth/v1/refresh")
                        .cookie(new Cookie(AuthCookieManager.REFRESH_COOKIE_NAME, OLD_REFRESH_TOKEN),
                                new Cookie(AuthCookieManager.CSRF_COOKIE_NAME, CSRF_TOKEN))
                        .header(AuthCookieManager.CSRF_HEADER_NAME, TAMPERED_CSRF_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(FORBIDDEN_CODE));

        verify(sessionAppService, never()).refresh(any());
    }

    @Test
    @DisplayName("POST /auth/v1/refresh：缺失 refresh Cookie 返回 401，且不触发刷新")
    void refreshShouldRejectMissingRefreshCookie() throws Exception {
        mockMvc.perform(post("/auth/v1/refresh")
                        .cookie(new Cookie(AuthCookieManager.CSRF_COOKIE_NAME, CSRF_TOKEN))
                        .header(AuthCookieManager.CSRF_HEADER_NAME, CSRF_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(UNAUTHORIZED_CODE));

        verify(sessionAppService, never()).refresh(any());
    }

    @Test
    @DisplayName("POST /auth/v1/logout：凭 Cookie 撤销 refreshToken 并清除两枚认证 Cookie")
    void logoutShouldRevokeAndClearCookies() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/v1/logout")
                        .header(HttpHeaders.AUTHORIZATION, BEARER_ACCESS)
                        .cookie(new Cookie(AuthCookieManager.REFRESH_COOKIE_NAME, OLD_REFRESH_TOKEN),
                                new Cookie(AuthCookieManager.CSRF_COOKIE_NAME, CSRF_TOKEN))
                        .header(AuthCookieManager.CSRF_HEADER_NAME, CSRF_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andReturn();

        verify(sessionAppService).logout(new LogoutCommand(BEARER_ACCESS, OLD_REFRESH_TOKEN));
        assertCookiesCleared(result);
    }

    @Test
    @DisplayName("POST /auth/v1/logout：CSRF 双提交校验失败返回 403，且不触发登出")
    void logoutShouldRejectWhenCsrfMissing() throws Exception {
        mockMvc.perform(post("/auth/v1/logout")
                        .header(HttpHeaders.AUTHORIZATION, BEARER_ACCESS)
                        .cookie(new Cookie(AuthCookieManager.REFRESH_COOKIE_NAME, OLD_REFRESH_TOKEN),
                                new Cookie(AuthCookieManager.CSRF_COOKIE_NAME, CSRF_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(FORBIDDEN_CODE));

        verify(sessionAppService, never()).logout(any());
    }

    @Test
    @DisplayName("POST /auth/v1/tenant/switch：旧 refreshToken 取自 Cookie，成功后轮换下发新 Cookie")
    void switchTenantShouldRotateCookies() throws Exception {
        when(sessionAppService.switchTenant(any())).thenReturn(loginResult(NEW_REFRESH_TOKEN));

        MvcResult result = mockMvc.perform(post("/auth/v1/tenant/switch")
                        .header(HttpHeaders.AUTHORIZATION, BEARER_ACCESS)
                        .cookie(new Cookie(AuthCookieManager.REFRESH_COOKIE_NAME, OLD_REFRESH_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.accessToken").value(ACCESS_TOKEN))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andReturn();

        assertAuthCookies(result, NEW_REFRESH_TOKEN);
        verify(sessionAppService).switchTenant(new SwitchTenantCommand(BEARER_ACCESS, 2L, OLD_REFRESH_TOKEN));
    }

    @Test
    @DisplayName("POST /auth/v1/login：用户名为空触发校验失败，返回统一参数错误体且不调用服务")
    void loginShouldRejectBlankUsername() throws Exception {
        mockMvc.perform(post("/auth/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"secret1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(PARAM_ERROR_CODE))
                .andExpect(jsonPath("$.message").value("用户名不能为空"));

        verify(sessionAppService, never()).login(any());
    }

    @Test
    @DisplayName("/auth/v1/me 权限契约：声明 @RequirePermission（仅需登录，无具体权限点）并正常返回当前用户")
    void meShouldDeclareRequirePermission() throws Exception {
        RequirePermission annotation = AuthController.class.getMethod("me").getAnnotation(RequirePermission.class);

        assertThat(annotation).as("/auth/v1/me 缺少 @RequirePermission").isNotNull();
        assertThat(annotation.value()).isEmpty();
        assertThat(annotation.requireLogin()).isTrue();

        when(sessionAppService.me(any())).thenReturn(currentUserResult());
        mockMvc.perform(get("/auth/v1/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.userId").value(USER_ID))
                .andExpect(jsonPath("$.data.menus[0].name").value("系统管理"));
        verify(sessionAppService).me(any());
    }

    @Test
    @DisplayName("凭据端点审计契约：login/logout 声明 @AuditLog 且 recordParams=false（凭据不落库）")
    void credentialEndpointsShouldNotRecordParams() throws NoSuchMethodException {
        AuditLog login = AuthController.class.getMethod("login", LoginRequest.class, HttpServletResponse.class)
                .getAnnotation(AuditLog.class);
        assertThat(login).as("/auth/v1/login 缺少 @AuditLog").isNotNull();
        assertThat(login.operation()).isEqualTo("用户登录");
        assertThat(login.recordParams()).isFalse();

        AuditLog logout = AuthController.class
                .getMethod("logout", String.class, HttpServletRequest.class, HttpServletResponse.class)
                .getAnnotation(AuditLog.class);
        assertThat(logout).as("/auth/v1/logout 缺少 @AuditLog").isNotNull();
        assertThat(logout.recordParams()).isFalse();
    }

    @Test
    @DisplayName("PUT /auth/v1/password：自助改密委托应用服务，且审计不记录口令")
    void changePasswordShouldDelegateAndNotRecordParams() throws Exception {
        mockMvc.perform(put("/auth/v1/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"Old#Pass1\",\"newPassword\":\"New#Pass2026\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()));

        verify(passwordAppService).changeOwnPassword(
                new ChangeOwnPasswordCommand("Old#Pass1", "New#Pass2026"));

        AuditLog auditLog = AuthController.class
                .getMethod("changePassword", ChangeOwnPasswordRequest.class)
                .getAnnotation(AuditLog.class);
        assertThat(auditLog).as("/auth/v1/password 缺少 @AuditLog").isNotNull();
        assertThat(auditLog.recordParams()).isFalse();
    }
}
