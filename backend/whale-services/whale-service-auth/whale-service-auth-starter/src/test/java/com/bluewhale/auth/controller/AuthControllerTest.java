package com.bluewhale.auth.controller;

import com.bluewhale.auth.application.auth.SessionAppService;
import com.bluewhale.auth.dto.LoginRequest;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.auth.vo.AuthMeResponse;
import com.bluewhale.auth.vo.AuthTokenIssue;
import com.bluewhale.auth.vo.LoginResponse;
import com.bluewhale.auth.vo.MenuVO;
import com.bluewhale.auth.web.AuthCookieManager;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.handler.GlobalExceptionHandler;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link AuthController} 控制器层单元测试（standalone MockMvc，V-03 Cookie 契约）。
 *
 * <p>覆盖：登录 / 刷新 / 切换租户 / 登出四个端点的 Cookie 行为（refresh 令牌只进
 * HttpOnly Cookie、CSRF 双提交 403 拦截、登出清除 Cookie）、响应体不含 refreshToken、
 * 请求体校验失败（统一参数错误体 {@code code=400}）、{@code /auth/me} 的
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

    private final AuthController controller = new AuthController(sessionAppService,
            new AuthCookieManager(false, 1440L));

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
     * 构造令牌签发结果
     *
     * @param refreshToken 签发的 refreshToken（仅写 Cookie）
     * @return 令牌签发结果（响应体不含 refreshToken）
     */
    private static AuthTokenIssue issue(String refreshToken) {
        LoginResponse response = new LoginResponse();
        response.setAccessToken(ACCESS_TOKEN);
        response.setTokenType("Bearer");
        response.setExpiresIn(EXPIRES_IN);
        response.setUsername("alice");
        response.setNickname("Alice");
        response.setRoles(List.of("admin"));
        response.setMustChangePassword(true);
        return new AuthTokenIssue(response, refreshToken);
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
            assertThat(cookie).contains("Path=/auth")
                    .contains("HttpOnly")
                    .contains("SameSite=Strict")
                    .contains("Max-Age=" + COOKIE_MAX_AGE_SECONDS)
                    .doesNotContain("Secure");
        });
        assertThat(cookies).anySatisfy(cookie -> {
            assertThat(cookie).matches(AuthCookieManager.CSRF_COOKIE_NAME + "=[0-9a-f]{64};.*");
            assertThat(cookie).contains("Path=/auth")
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
            assertThat(cookie).contains("Max-Age=0").contains("HttpOnly").contains("Path=/auth");
        });
        assertThat(cookies).anySatisfy(cookie -> {
            assertThat(cookie).startsWith(AuthCookieManager.CSRF_COOKIE_NAME + "=");
            assertThat(cookie).contains("Max-Age=0").contains("Path=/auth").doesNotContain("HttpOnly");
        });
    }

    @Test
    @DisplayName("POST /auth/login：返回 code=0，refreshToken 只进 Cookie、响应体不可见")
    void loginShouldSetCookiesAndHideRefreshToken() throws Exception {
        when(sessionAppService.login(isNull(), eq("alice"), eq("secret1"))).thenReturn(issue(OLD_REFRESH_TOKEN));

        MvcResult result = mockMvc.perform(post("/auth/login")
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
        verify(sessionAppService).login(null, "alice", "secret1");
    }

    @Test
    @DisplayName("POST /auth/refresh：凭 Cookie + CSRF 双提交换发，轮换下发新 Cookie")
    void refreshShouldRotateFromCookie() throws Exception {
        when(sessionAppService.refresh(OLD_REFRESH_TOKEN)).thenReturn(issue(NEW_REFRESH_TOKEN));

        MvcResult result = mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie(AuthCookieManager.REFRESH_COOKIE_NAME, OLD_REFRESH_TOKEN),
                                new Cookie(AuthCookieManager.CSRF_COOKIE_NAME, CSRF_TOKEN))
                        .header(AuthCookieManager.CSRF_HEADER_NAME, CSRF_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.accessToken").value(ACCESS_TOKEN))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andReturn();

        assertAuthCookies(result, NEW_REFRESH_TOKEN);
        verify(sessionAppService).refresh(OLD_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("POST /auth/refresh：缺失 X-CSRF-Token 头返回 403，且不触发刷新")
    void refreshShouldRejectMissingCsrfHeader() throws Exception {
        mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie(AuthCookieManager.REFRESH_COOKIE_NAME, OLD_REFRESH_TOKEN),
                                new Cookie(AuthCookieManager.CSRF_COOKIE_NAME, CSRF_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(FORBIDDEN_CODE));

        verify(sessionAppService, never()).refresh(any());
    }

    @Test
    @DisplayName("POST /auth/refresh：X-CSRF-Token 头与 Cookie 不等返回 403")
    void refreshShouldRejectMismatchedCsrf() throws Exception {
        mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie(AuthCookieManager.REFRESH_COOKIE_NAME, OLD_REFRESH_TOKEN),
                                new Cookie(AuthCookieManager.CSRF_COOKIE_NAME, CSRF_TOKEN))
                        .header(AuthCookieManager.CSRF_HEADER_NAME, TAMPERED_CSRF_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(FORBIDDEN_CODE));

        verify(sessionAppService, never()).refresh(any());
    }

    @Test
    @DisplayName("POST /auth/refresh：缺失 refresh Cookie 返回 401，且不触发刷新")
    void refreshShouldRejectMissingRefreshCookie() throws Exception {
        mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie(AuthCookieManager.CSRF_COOKIE_NAME, CSRF_TOKEN))
                        .header(AuthCookieManager.CSRF_HEADER_NAME, CSRF_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(UNAUTHORIZED_CODE));

        verify(sessionAppService, never()).refresh(any());
    }

    @Test
    @DisplayName("POST /auth/logout：凭 Cookie 撤销 refreshToken 并清除两枚认证 Cookie")
    void logoutShouldRevokeAndClearCookies() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, BEARER_ACCESS)
                        .cookie(new Cookie(AuthCookieManager.REFRESH_COOKIE_NAME, OLD_REFRESH_TOKEN),
                                new Cookie(AuthCookieManager.CSRF_COOKIE_NAME, CSRF_TOKEN))
                        .header(AuthCookieManager.CSRF_HEADER_NAME, CSRF_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andReturn();

        verify(sessionAppService).logout(BEARER_ACCESS, OLD_REFRESH_TOKEN);
        assertCookiesCleared(result);
    }

    @Test
    @DisplayName("POST /auth/logout：CSRF 双提交校验失败返回 403，且不触发登出")
    void logoutShouldRejectWhenCsrfMissing() throws Exception {
        mockMvc.perform(post("/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, BEARER_ACCESS)
                        .cookie(new Cookie(AuthCookieManager.REFRESH_COOKIE_NAME, OLD_REFRESH_TOKEN),
                                new Cookie(AuthCookieManager.CSRF_COOKIE_NAME, CSRF_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(FORBIDDEN_CODE));

        verify(sessionAppService, never()).logout(any(), any());
    }

    @Test
    @DisplayName("POST /auth/tenant/switch：旧 refreshToken 取自 Cookie，成功后轮换下发新 Cookie")
    void switchTenantShouldRotateCookies() throws Exception {
        when(sessionAppService.switchTenant(eq(BEARER_ACCESS), eq(2L), eq(OLD_REFRESH_TOKEN)))
                .thenReturn(issue(NEW_REFRESH_TOKEN));

        MvcResult result = mockMvc.perform(post("/auth/tenant/switch")
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

        verify(sessionAppService, never()).login(any(), any(), any());
    }

    @Test
    @DisplayName("/auth/me 权限契约：声明 @RequirePermission（仅需登录，无具体权限点）并正常返回当前用户")
    void meShouldDeclareRequirePermission() throws Exception {
        RequirePermission annotation = AuthController.class.getMethod("me").getAnnotation(RequirePermission.class);

        assertThat(annotation).as("/auth/me 缺少 @RequirePermission").isNotNull();
        assertThat(annotation.value()).isEmpty();
        assertThat(annotation.requireLogin()).isTrue();

        when(sessionAppService.me()).thenReturn(authMeResponse());
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.userId").value(USER_ID))
                .andExpect(jsonPath("$.data.menus[0].name").value("系统管理"));
        verify(sessionAppService).me();
    }

    @Test
    @DisplayName("凭据端点审计契约：login/logout 声明 @AuditLog 且 recordParams=false（凭据不落库）")
    void credentialEndpointsShouldNotRecordParams() throws NoSuchMethodException {
        AuditLog login = AuthController.class.getMethod("login", LoginRequest.class, HttpServletResponse.class)
                .getAnnotation(AuditLog.class);
        assertThat(login).as("/auth/login 缺少 @AuditLog").isNotNull();
        assertThat(login.operation()).isEqualTo("用户登录");
        assertThat(login.recordParams()).isFalse();

        AuditLog logout = AuthController.class
                .getMethod("logout", String.class, HttpServletRequest.class, HttpServletResponse.class)
                .getAnnotation(AuditLog.class);
        assertThat(logout).as("/auth/logout 缺少 @AuditLog").isNotNull();
        assertThat(logout.recordParams()).isFalse();
    }
}
