package com.bluewhale.auth.interfaces.web;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * 认证 Cookie 管理器（V-03）：refresh 令牌 HttpOnly Cookie 下发与 CSRF 双提交校验。
 *
 * <p>refresh 令牌不再出现在任何响应体：登录 / 刷新 / 切换租户成功后以
 * {@code Set-Cookie: whale_refresh_token} 下发（HttpOnly + Path=/auth + SameSite=Strict，
 * Secure 由 {@code whale.auth.cookie.secure} 控制），MaxAge 与 refreshToken 有效期对齐；
 * 同时下发 / 轮换 CSRF Cookie {@code whale_csrf}（非 HttpOnly，供前端读取后经
 * {@code X-CSRF-Token} 头回传）。{@code /auth/refresh} 与 {@code /auth/logout} 按
 * 「双提交」校验：请求头值必须等于 Cookie 值，缺失或不等一律 403。</p>
 *
 * @author BlueWhale
 */
@Component
public class AuthCookieManager {

    /** refresh 令牌 Cookie 名 */
    public static final String REFRESH_COOKIE_NAME = "whale_refresh_token";

    /** CSRF Cookie 名（双提交的一半，前端可读） */
    public static final String CSRF_COOKIE_NAME = "whale_csrf";

    /** CSRF 请求头名（双提交的另一半） */
    public static final String CSRF_HEADER_NAME = "X-CSRF-Token";

    /** 认证 Cookie 作用路径：仅认证端点携带，避免随业务请求外发 */
    private static final String COOKIE_PATH = "/auth";

    /** CSRF Cookie 作用路径：整站可读（前端在任意业务页面都需读取后回传 X-CSRF-Token） */
    private static final String CSRF_COOKIE_PATH = "/";

    /** SameSite 属性取值 */
    private static final String SAME_SITE_STRICT = "Strict";

    /** CSRF token 随机字节数（hex 编码后 64 字符） */
    private static final int CSRF_TOKEN_BYTES = 32;

    /** 清除 Cookie 时的 MaxAge（立即过期） */
    private static final long MAX_AGE_EXPIRED = 0L;

    /** 未携带 refresh Cookie 的统一对外文案 */
    private static final String MISSING_REFRESH_COOKIE_MESSAGE = "登录状态已过期，请重新登录";

    /** CSRF token 随机源（SecureRandom 线程安全） */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** 是否为认证 Cookie 附加 Secure 标记（HTTPS 站点开启，配置项 whale.auth.cookie.secure） */
    private final boolean secure;

    /** refreshToken 有效期（分钟），用于对齐 Cookie MaxAge（配置项 whale.auth.jwt.refresh-expire-minutes） */
    private final long refreshExpireMinutes;

    /**
     * 构造认证 Cookie 管理器
     *
     * <p>仅接收原始配置值，避免接口层反向依赖基础设施层的配置类（分层架构约束）。</p>
     *
     * @param secure 是否为认证 Cookie 附加 Secure 标记
     * @param refreshExpireMinutes refreshToken 有效期（分钟）
     */
    public AuthCookieManager(@Value("${whale.auth.cookie.secure:false}") boolean secure,
                             @Value("${whale.auth.jwt.refresh-expire-minutes:1440}") long refreshExpireMinutes) {
        this.secure = secure;
        this.refreshExpireMinutes = refreshExpireMinutes;
    }

    /**
     * 登录 / 刷新 / 切换租户成功后下发 Cookie：refresh 令牌 + 轮换后的 CSRF token
     *
     * @param response 当前 HTTP 响应
     * @param refreshToken 新签发的 refreshToken（仅写入 Cookie，响应体不可见）
     */
    public void writeAuthCookies(HttpServletResponse response, String refreshToken) {
        long maxAgeSeconds = refreshExpireMinutes * 60;
        ResponseCookie refreshCookie = ResponseCookie.from(REFRESH_COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .path(COOKIE_PATH)
                .sameSite(SAME_SITE_STRICT)
                .maxAge(maxAgeSeconds)
                .secure(secure)
                .build();
        ResponseCookie csrfCookie = ResponseCookie.from(CSRF_COOKIE_NAME, newCsrfToken())
                .httpOnly(false)
                .path(CSRF_COOKIE_PATH)
                .sameSite(SAME_SITE_STRICT)
                .maxAge(maxAgeSeconds)
                .secure(secure)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, csrfCookie.toString());
    }

    /**
     * 登出成功后清除两枚认证 Cookie（MaxAge=0 立即过期）
     *
     * @param response 当前 HTTP 响应
     */
    public void clearAuthCookies(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, expiredCookie(REFRESH_COOKIE_NAME, COOKIE_PATH, true).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, expiredCookie(CSRF_COOKIE_NAME, CSRF_COOKIE_PATH, false).toString());
    }

    /**
     * 从请求 Cookie 中解析 refresh 令牌
     *
     * @param request 当前 HTTP 请求
     * @return refresh 令牌；Cookie 缺失时返回 {@code null}
     */
    public String resolveRefreshToken(HttpServletRequest request) {
        return resolveCookieValue(request, REFRESH_COOKIE_NAME);
    }

    /**
     * 从请求 Cookie 中解析 refresh 令牌，缺失即拒绝（V-03：refresh / logout 的唯一令牌入口）
     *
     * @param request 当前 HTTP 请求
     * @return refresh 令牌原文
     * @throws BizException Cookie 缺失或空白时抛出 401
     */
    public String requireRefreshToken(HttpServletRequest request) {
        String refreshToken = resolveRefreshToken(request);
        if (!StringUtils.hasText(refreshToken)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, MISSING_REFRESH_COOKIE_MESSAGE);
        }
        return refreshToken;
    }

    /**
     * CSRF 双提交校验：{@code X-CSRF-Token} 请求头必须等于 {@code whale_csrf} Cookie 值
     *
     * @param request 当前 HTTP 请求
     * @throws BizException 头或 Cookie 缺失、两者不等时抛出 403
     */
    public void verifyCsrf(HttpServletRequest request) {
        String headerValue = request.getHeader(CSRF_HEADER_NAME);
        String cookieValue = resolveCookieValue(request, CSRF_COOKIE_NAME);
        if (StringUtils.hasText(headerValue) && headerValue.equals(cookieValue)) {
            return;
        }
        throw new BizException(ErrorCode.FORBIDDEN, "CSRF 校验失败，请重新登录");
    }

    /**
     * 生成新 CSRF token：32 字节 SecureRandom 随机数 hex 编码
     *
     * @return 64 字符 hex 字符串
     */
    private static String newCsrfToken() {
        byte[] bytes = new byte[CSRF_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * 按名称读取请求 Cookie 值
     *
     * @param request 当前 HTTP 请求
     * @param name Cookie 名
     * @return Cookie 值；不存在时返回 {@code null}
     */
    private static String resolveCookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /**
     * 构造立即过期的清除 Cookie（MaxAge=0，属性与下发时保持一致以精确覆盖）
     *
     * @param name Cookie 名
     * @param path 下发时使用的 Path（必须一致才能精确覆盖删除）
     * @param httpOnly 是否 HttpOnly
     * @return 清除用 Cookie
     */
    private ResponseCookie expiredCookie(String name, String path, boolean httpOnly) {
        return ResponseCookie.from(name, "")
                .httpOnly(httpOnly)
                .path(path)
                .sameSite(SAME_SITE_STRICT)
                .maxAge(MAX_AGE_EXPIRED)
                .secure(secure)
                .build();
    }
}
