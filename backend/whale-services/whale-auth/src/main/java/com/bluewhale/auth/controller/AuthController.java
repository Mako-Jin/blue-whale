package com.bluewhale.auth.controller;

import com.bluewhale.auth.dto.LoginRequest;
import com.bluewhale.auth.dto.RefreshRequest;
import com.bluewhale.auth.service.AuthService;
import com.bluewhale.auth.vo.LoginResponse;
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

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return Result.ok(authService.refresh(request));
    }

    /**
     * 登出：使请求头中的 accessToken 立即失效（写入分布式黑名单）
     *
     * @param authorization 请求头 {@code Authorization: Bearer <token>}，允许缺失
     */
    @PostMapping("/logout")
    public Result<Void> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        authService.logout(authorization);
        return Result.ok();
    }
}
