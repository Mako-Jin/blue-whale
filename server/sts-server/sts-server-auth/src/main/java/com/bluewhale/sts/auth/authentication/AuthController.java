package com.bluewhale.sts.auth.authentication;

import com.bluewhale.sts.common.basic.model.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。
 *
 * <p>本组接口是**公开**的（不标 {@code @RequiresPermission}），
 * 否则会形成"要登录先得有令牌"的死锁。
 * 它们的安全性由服务层保证（口令校验、失败锁定、令牌轮换）。
 *
 * <p>{@code /api/auth/me} 例外：它要求已认证，但没有"权限"要求 ——
 * 任何登录用户都应能查到自己的信息。这一要求在 {@code AuthService#currentUser}
 * 里通过 {@code AuthContextHolder.require()} 表达。
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "认证", description = "登录、登出、刷新令牌、当前用户")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "登录", description = "以租户编码 + 登录名 + 口令登录，返回访问令牌与刷新令牌")
    public R<LoginVO> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return R.ok(authService.login(request, resolveIp(httpRequest), httpRequest.getHeader("User-Agent")));
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新令牌", description = "用刷新令牌换取新的令牌对；旧刷新令牌随之作废")
    public R<LoginVO> refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest httpRequest) {
        return R.ok(authService.refresh(request, resolveIp(httpRequest), httpRequest.getHeader("User-Agent")));
    }

    @PostMapping("/logout")
    @Operation(summary = "登出", description = "撤销刷新令牌；不传则撤销该用户全部刷新令牌")
    public R<Void> logout(@RequestBody(required = false) RefreshRequest request) {
        authService.logout(request);
        return R.ok();
    }

    @GetMapping("/me")
    @Operation(summary = "当前登录用户", description = "返回当前用户身份、角色与权限")
    public R<MeVO> me() {
        return R.ok(authService.currentUser());
    }

    /**
     * 解析客户端 IP。
     *
     * <p>优先取 {@code X-Forwarded-For} 的**第一段**：经代理时 {@code getRemoteAddr()}
     * 拿到的是最后一跳代理地址，记下来没有排查价值。
     *
     * <p>注意该头可被伪造，因此只用于排查参考，**不作为鉴权或风控依据** ——
     * 与 {@code ClientLogController} 的处理保持一致。
     */
    private static String resolveIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }

}
