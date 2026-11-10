package com.bluewhale.auth.interfaces.controller;

import com.bluewhale.auth.application.auth.SessionAppService;
import com.bluewhale.auth.application.user.password.ChangeOwnPasswordCommand;
import com.bluewhale.auth.application.user.password.PasswordAppService;
import com.bluewhale.auth.interfaces.dto.ChangeOwnPasswordRequest;
import com.bluewhale.auth.interfaces.dto.LoginRequest;
import com.bluewhale.auth.interfaces.dto.SwitchTenantRequest;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.auth.application.vo.AuthMeResponse;
import com.bluewhale.auth.application.vo.AuthTokenIssue;
import com.bluewhale.auth.application.vo.LoginResponse;
import com.bluewhale.auth.application.vo.MyTenantVO;
import com.bluewhale.auth.interfaces.web.AuthCookieManager;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 认证接口：登录 / 刷新 Token / 登出（V-03：refresh 令牌走 HttpOnly Cookie + CSRF 双提交）
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SessionAppService sessionAppService;

    private final PasswordAppService passwordAppService;

    private final AuthCookieManager cookieManager;

    /**
     * 登录：成功与失败均留痕（失败原因记录在 error_msg）。
     * <p>{@code recordParams = false}：请求体含明文密码，凭据绝不落库。</p>
     * <p>成功后下发两枚 Cookie（V-03）：{@code whale_refresh_token}（HttpOnly）与
     * {@code whale_csrf}（前端可读），响应体不再返回 refreshToken。</p>
     *
     * @param request 登录请求（用户名与明文密码）
     * @param response 当前 HTTP 响应（写入 Set-Cookie）
     * @return 登录响应（含 accessToken，不含 refreshToken）
     */
    @PostMapping("/login")
    @AuditLog(operation = "用户登录", type = OperateType.LOGIN, targetType = "sys_user", recordParams = false)
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthTokenIssue issue = sessionAppService.login(
                request.getTenantCode(), request.getUsername(), request.getPassword());
        cookieManager.writeAuthCookies(response, issue.getRefreshToken());
        return Result.ok(issue.getResponse());
    }

    /**
     * 刷新 Token：凭 {@code whale_refresh_token} Cookie 换发新令牌（请求体不再传入 refreshToken）。
     * <p>先做 CSRF 双提交校验（{@code X-CSRF-Token} 头必须等于 {@code whale_csrf} Cookie），
     * 缺失或不等一律 403；成功后轮换下发新 Cookie，响应体不返回 refreshToken。</p>
     *
     * @param request 当前 HTTP 请求（读取 refresh / CSRF Cookie）
     * @param response 当前 HTTP 响应（轮换写入 Set-Cookie）
     * @return 登录响应（含新 accessToken，不含 refreshToken）
     */
    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        cookieManager.verifyCsrf(request);
        AuthTokenIssue issue = sessionAppService.refresh(cookieManager.requireRefreshToken(request));
        cookieManager.writeAuthCookies(response, issue.getRefreshToken());
        return Result.ok(issue.getResponse());
    }

    /**
     * 当前登录用户：角色 / 权限码 / 可见菜单树（前端动态路由与按钮级权限渲染的数据源）。
     * <p>仅需登录，不要求具体权限点。</p>
     *
     * @return 当前登录用户的角色、权限码与可见菜单树
     */
    @GetMapping("/me")
    @RequirePermission
    public Result<AuthMeResponse> me() {
        return Result.ok(sessionAppService.me());
    }

    /**
     * 自助改密：当前登录用户修改本人口令（REQ-SEC-023）。
     *
     * <p>{@code recordParams = false}：请求体含新旧明文口令，凭据绝不落库。
     * 改密成功后该用户全部存量令牌被撤销，前端应清理登录态并重新登录。</p>
     *
     * @param request 自助改密请求（旧口令 + 新口令）
     * @return 操作结果
     */
    @PutMapping("/password")
    @AuditLog(operation = "修改本人密码", type = OperateType.UPDATE, targetType = "sys_user", recordParams = false)
    public Result<Void> changePassword(@Valid @RequestBody ChangeOwnPasswordRequest request) {
        passwordAppService.changeOwnPassword(
                new ChangeOwnPasswordCommand(request.getOldPassword(), request.getNewPassword()));
        return Result.ok();
    }

    /**
     * 我的租户：当前用户可进入的租户列表（仅需登录，供前端切换器判断是否展示）。
     *
     * @return 启用且当前可用的租户列表，当前令牌租户标记 {@code current=true}
     */
    @GetMapping("/tenants")
    @RequirePermission
    public Result<List<MyTenantVO>> listTenants() {
        return Result.ok(sessionAppService.listTenants());
    }

    /**
     * 切换租户：同一 {@code userId} / {@code sub} 只更换令牌租户上下文与角色。
     * <p>{@code recordParams = false}：凭据绝不落库。旧 refreshToken 改由 Cookie 自动携带
     * （V-03），切换成功后旧 access/refresh 一并失效，并以 Cookie 下发新令牌。</p>
     *
     * @param authorization 请求头 {@code Authorization: Bearer <token>}（可空）
     * @param request 切换请求（目标租户）
     * @param servletRequest 当前 HTTP 请求（读取 refresh Cookie）
     * @param response 当前 HTTP 响应（写入 Set-Cookie）
     * @return 目标租户下的登录响应（新 accessToken，不含 refreshToken）
     */
    @PostMapping("/tenant/switch")
    @AuditLog(operation = "切换租户", type = OperateType.OTHER, targetType = "sys_user", recordParams = false)
    public Result<LoginResponse> switchTenant(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody SwitchTenantRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse response) {
        AuthTokenIssue issue = sessionAppService.switchTenant(
                authorization, request.getTenantId(), cookieManager.resolveRefreshToken(servletRequest));
        cookieManager.writeAuthCookies(response, issue.getRefreshToken());
        return Result.ok(issue.getResponse());
    }

    /**
     * 登出：使请求头中的 accessToken 与 {@code whale_refresh_token} Cookie 中的 refreshToken
     * 立即失效（双写分布式黑名单），并清除两枚认证 Cookie。
     * <p>须携带 {@code whale_refresh_token} Cookie 并通过 CSRF 双提交校验。</p>
     *
     * @param authorization 请求头 {@code Authorization: Bearer <token>}，允许缺失
     * @param request 当前 HTTP 请求（读取 refresh / CSRF Cookie）
     * @param response 当前 HTTP 响应（清除 Set-Cookie）
     */
    @PostMapping("/logout")
    @AuditLog(operation = "用户登出", type = OperateType.LOGOUT, targetType = "sys_user", recordParams = false)
    public Result<Void> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            HttpServletRequest request,
            HttpServletResponse response) {
        cookieManager.verifyCsrf(request);
        sessionAppService.logout(authorization, cookieManager.requireRefreshToken(request));
        cookieManager.clearAuthCookies(response);
        return Result.ok();
    }
}
