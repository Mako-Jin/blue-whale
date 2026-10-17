package com.bluewhale.auth.controller;

import com.bluewhale.auth.dto.LoginRequest;
import com.bluewhale.auth.dto.LogoutRequest;
import com.bluewhale.auth.dto.RefreshRequest;
import com.bluewhale.auth.service.AuthService;
import com.bluewhale.auth.vo.LoginResponse;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：登录 / 刷新 Token / 登出
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 登录：成功与失败均留痕（失败原因记录在 error_msg）。
     * <p>{@code recordParams = false}：请求体含明文密码，凭据绝不落库。</p>
     */
    @PostMapping("/login")
    @AuditLog(operation = "用户登录", type = OperateType.LOGIN, targetType = "sys_user", recordParams = false)
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return Result.ok(authService.refresh(request));
    }

    /**
     * 登出：使请求头中的 accessToken 与请求体中的 refreshToken 立即失效（双写分布式黑名单）
     *
     * @param authorization 请求头 {@code Authorization: Bearer <token>}，允许缺失
     * @param request       请求体，可携带 {@code refreshToken} 使其一并失效（可空）
     */
    @PostMapping("/logout")
    @AuditLog(operation = "用户登出", type = OperateType.LOGOUT, targetType = "sys_user", recordParams = false)
    public Result<Void> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody(required = false) LogoutRequest request) {
        authService.logout(authorization, request == null ? null : request.getRefreshToken());
        return Result.ok();
    }
}
