package com.bluewhale.auth.web;

import com.bluewhale.common.core.exception.BizException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link AuthCookieManager} 单元测试（V-03）：refresh / CSRF Cookie 标志契约与双提交校验。
 *
 * <p>覆盖：HttpOnly / SameSite=Strict / Path=/auth / MaxAge 与 refreshToken 有效期对齐 /
 * Secure 开关（{@code whale.auth.cookie.secure}）、CSRF token 32 字节随机 hex、
 * 双提交校验通过 / 缺失 / 篡改三种分支、登出清除 Cookie（MaxAge=0）。</p>
 *
 * @author BlueWhale
 */
class AuthCookieManagerTest {

    private static final String REFRESH_TOKEN = "refresh-token-value";
    private static final long COOKIE_MAX_AGE_SECONDS = 86400L;
    private static final int UNAUTHORIZED_CODE = 401;
    private static final int FORBIDDEN_CODE = 403;

    /** refreshToken 有效期（分钟），与 dev 默认配置一致 */
    private static final long REFRESH_EXPIRE_MINUTES = 1440L;

    private AuthCookieManager cookieManager;

    private MockHttpServletResponse response;

    /**
     * 每个用例重置为 dev 默认配置（Secure 关闭，refresh 有效期 1440 分钟）
     */
    @BeforeEach
    void setUp() {
        cookieManager = new AuthCookieManager(false, REFRESH_EXPIRE_MINUTES);
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("writeAuthCookies：refresh Cookie 带 HttpOnly / Path=/auth / SameSite=Strict，且 MaxAge 对齐有效期")
    void writeAuthCookiesShouldSetRefreshCookieFlags() {
        cookieManager.writeAuthCookies(response, REFRESH_TOKEN);

        List<String> cookies = response.getHeaders("Set-Cookie");
        assertThat(cookies).anySatisfy(cookie -> {
            assertThat(cookie).startsWith(AuthCookieManager.REFRESH_COOKIE_NAME + "=" + REFRESH_TOKEN);
            assertThat(cookie).contains("Path=/auth")
                    .contains("HttpOnly")
                    .contains("SameSite=Strict")
                    .contains("Max-Age=" + COOKIE_MAX_AGE_SECONDS)
                    .doesNotContain("Secure");
        });
    }

    @Test
    @DisplayName("writeAuthCookies：CSRF Cookie 前端可读（非 HttpOnly），值为 32 字节随机 hex")
    void writeAuthCookiesShouldSetReadableCsrfCookie() {
        cookieManager.writeAuthCookies(response, REFRESH_TOKEN);

        List<String> cookies = response.getHeaders("Set-Cookie");
        assertThat(cookies).anySatisfy(cookie -> {
            assertThat(cookie).matches(AuthCookieManager.CSRF_COOKIE_NAME + "=[0-9a-f]{64};.*");
            assertThat(cookie).contains("Path=/auth")
                    .contains("SameSite=Strict")
                    .contains("Max-Age=" + COOKIE_MAX_AGE_SECONDS)
                    .doesNotContain("HttpOnly")
                    .doesNotContain("Secure");
        });
    }

    @Test
    @DisplayName("writeAuthCookies：secure=true 时两枚 Cookie 均追加 Secure 标记")
    void writeAuthCookiesShouldAppendSecureWhenEnabled() {
        cookieManager = new AuthCookieManager(true, REFRESH_EXPIRE_MINUTES);
        cookieManager.writeAuthCookies(response, REFRESH_TOKEN);

        List<String> cookies = response.getHeaders("Set-Cookie");
        String refreshCookie = cookieHeader(cookies, AuthCookieManager.REFRESH_COOKIE_NAME);
        String csrfCookie = cookieHeader(cookies, AuthCookieManager.CSRF_COOKIE_NAME);
        assertThat(refreshCookie).contains("Secure").contains("HttpOnly");
        assertThat(csrfCookie).contains("Secure").doesNotContain("HttpOnly");
    }

    @Test
    @DisplayName("writeAuthCookies：每次调用轮换 CSRF token（两次取值不同）")
    void writeAuthCookiesShouldRotateCsrfToken() {
        cookieManager.writeAuthCookies(response, REFRESH_TOKEN);
        String first = csrfValue(response.getHeaders("Set-Cookie"));
        response = new MockHttpServletResponse();
        cookieManager.writeAuthCookies(response, REFRESH_TOKEN);
        String second = csrfValue(response.getHeaders("Set-Cookie"));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("clearAuthCookies：两枚 Cookie 置 Max-Age=0 立即过期")
    void clearAuthCookiesShouldExpireBoth() {
        cookieManager.clearAuthCookies(response);

        List<String> cookies = response.getHeaders("Set-Cookie");
        assertThat(cookieHeader(cookies, AuthCookieManager.REFRESH_COOKIE_NAME)).contains("Max-Age=0");
        assertThat(cookieHeader(cookies, AuthCookieManager.CSRF_COOKIE_NAME)).contains("Max-Age=0");
    }

    @Test
    @DisplayName("resolveRefreshToken：从请求 Cookie 中取回 refresh 令牌，缺失返回 null")
    void resolveRefreshTokenShouldReadCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThat(cookieManager.resolveRefreshToken(request)).isNull();

        request.setCookies(new Cookie(AuthCookieManager.REFRESH_COOKIE_NAME, REFRESH_TOKEN));
        assertThat(cookieManager.resolveRefreshToken(request)).isEqualTo(REFRESH_TOKEN);
    }

    @Test
    @DisplayName("requireRefreshToken：Cookie 缺失时抛 401")
    void requireRefreshTokenShouldRejectMissingCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        BizException ex = assertThrows(BizException.class, () -> cookieManager.requireRefreshToken(request));

        assertThat(ex.getCode()).isEqualTo(UNAUTHORIZED_CODE);
    }

    @Test
    @DisplayName("verifyCsrf：请求头与 Cookie 相等时放行")
    void verifyCsrfShouldPassWhenHeaderMatchesCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AuthCookieManager.CSRF_COOKIE_NAME, "token-1"));
        request.addHeader(AuthCookieManager.CSRF_HEADER_NAME, "token-1");

        cookieManager.verifyCsrf(request);
    }

    @Test
    @DisplayName("verifyCsrf：请求头缺失或与 Cookie 不等一律 403")
    void verifyCsrfShouldRejectMissingOrTamperedHeader() {
        MockHttpServletRequest missing = new MockHttpServletRequest();
        missing.setCookies(new Cookie(AuthCookieManager.CSRF_COOKIE_NAME, "token-1"));
        MockHttpServletRequest tampered = new MockHttpServletRequest();
        tampered.setCookies(new Cookie(AuthCookieManager.CSRF_COOKIE_NAME, "token-1"));
        tampered.addHeader(AuthCookieManager.CSRF_HEADER_NAME, "token-2");

        BizException missingEx = assertThrows(BizException.class, () -> cookieManager.verifyCsrf(missing));
        BizException tamperedEx = assertThrows(BizException.class, () -> cookieManager.verifyCsrf(tampered));

        assertThat(missingEx.getCode()).isEqualTo(FORBIDDEN_CODE);
        assertThat(tamperedEx.getCode()).isEqualTo(FORBIDDEN_CODE);
    }

    /**
     * 从 Set-Cookie 头列表中取出指定名称的 Cookie 头
     *
     * @param cookies Set-Cookie 头列表
     * @param name Cookie 名
     * @return 匹配的 Cookie 头原文
     */
    private static String cookieHeader(List<String> cookies, String name) {
        return cookies.stream()
                .filter(cookie -> cookie.startsWith(name + "="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少 Cookie: " + name));
    }

    /**
     * 从 Set-Cookie 头列表中提取 CSRF Cookie 值
     *
     * @param cookies Set-Cookie 头列表
     * @return CSRF token 值
     */
    private static String csrfValue(List<String> cookies) {
        String header = cookieHeader(cookies, AuthCookieManager.CSRF_COOKIE_NAME);
        return header.substring((AuthCookieManager.CSRF_COOKIE_NAME + "=").length(),
                header.indexOf(';'));
    }
}
