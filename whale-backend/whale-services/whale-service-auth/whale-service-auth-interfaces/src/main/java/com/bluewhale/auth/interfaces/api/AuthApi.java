package com.bluewhale.auth.interfaces.api;

import com.bluewhale.auth.interfaces.dto.request.ChangeOwnPasswordRequest;
import com.bluewhale.auth.interfaces.dto.request.LoginRequest;
import com.bluewhale.auth.interfaces.dto.request.SwitchTenantRequest;
import com.bluewhale.auth.interfaces.dto.response.CurrentUserResponse;
import com.bluewhale.auth.interfaces.dto.response.LoginResponse;
import com.bluewhale.auth.interfaces.dto.response.TenantResponse;
import com.bluewhale.auth.sdk.annotation.RequireReauth;
import com.bluewhale.common.core.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * 认证 API 契约：登录 / 刷新 Token / 登出（V-03：refresh 令牌走 HttpOnly Cookie + CSRF 双提交）。
 *
 * <p>本接口只声明 HTTP 契约（路径、方法、参数与返回 DTO），不包含任何业务实现；
 * 实现类位于 starter 层（Web 适配层）。</p>
 *
 * <p><b>Spring Boot 4 API Versioning</b>：类级 {@code version = "1"} 声明 API 版本，
 * 路径首段 {@code /{version}} 由 {@code PathApiVersionResolver} 解析（如 {@code /auth/v1/login}）。
 * 版本段必须以 URI 变量声明，否则无法参与路径匹配。</p>
 *
 * @author BlueWhale
 */
@RequestMapping(path = "/auth/v{version}", version = "1")
public interface AuthApi {

    /**
     * 登录：成功与失败均留痕（失败原因记录在 error_msg）。
     * <p>成功后下发两枚 Cookie（V-03）：{@code whale_refresh_token}（HttpOnly）与
     * {@code whale_csrf}（前端可读），响应体不再返回 refreshToken。</p>
     *
     * @param request 登录请求（用户名与明文密码）
     * @param response 当前 HTTP 响应（写入 Set-Cookie）
     * @return 登录响应（含 accessToken，不含 refreshToken）
     */
    @PostMapping("/login")
    Result<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response);

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
    Result<LoginResponse> refresh(HttpServletRequest request, HttpServletResponse response);

    /**
     * 当前登录用户：角色 / 权限码 / 可见菜单树（前端动态路由与按钮级权限渲染的数据源）。
     * <p>仅需登录，不要求具体权限点。</p>
     *
     * @return 当前登录用户的角色、权限码与可见菜单树
     */
    @GetMapping("/me")
    Result<CurrentUserResponse> me();

    /**
     * 自助改密：当前登录用户修改本人口令（REQ-SEC-023）。
     *
     * <p>改密成功后该用户全部存量令牌被撤销，前端应清理登录态并重新登录。</p>
     *
     * @param request 自助改密请求（旧口令 + 新口令）
     * @return 操作结果
     *
     * 危险操作：受 {@code @RequireReauth} 二次认证约束（Wave 6.22，需 X-Reauth-Token）。
     *
     * <p><b>例外（Wave 6.32）：自助改密不标 {@code @RequireReauth}。</b>
     * 请求体里的原口令已经构成 step-up 证明（服务端逐字校验），再要一张 step-up 凭证是重复；
     * 更关键的是会与「待改密」状态互锁——拦截器返回 428 要求先验密，而 {@code POST /auth/v1/reauth}
     * 对 {@code must_change_password} / 口令过期的用户直接 403「请先修改口令」，
     * 于是这类用户既改不了口令也拿不到凭证，除超管重置外无路可走。</p>
     */
    @PutMapping("/password")
    Result<Void> changePassword(@Valid @RequestBody ChangeOwnPasswordRequest request);

    /**
     * 我的租户：当前用户可进入的租户列表（仅需登录，供前端切换器判断是否展示）。
     *
     * @return 启用且当前可用的租户列表，当前令牌租户标记 {@code current=true}
     */
    @GetMapping("/tenants")
    Result<List<TenantResponse>> listTenants();

    /**
     * 切换租户：同一 {@code userId} / {@code sub} 只更换令牌租户上下文与角色。
     * <p>旧 refreshToken 改由 Cookie 自动携带（V-03），切换成功后旧 access/refresh 一并失效，
     * 并以 Cookie 下发新令牌。</p>
     *
     * @param authorization 请求头 {@code Authorization: Bearer <token>}（可空）
     * @param request 切换请求（目标租户）
     * @param servletRequest 当前 HTTP 请求（读取 refresh Cookie）
     * @param response 当前 HTTP 响应（写入 Set-Cookie）
     * @return 目标租户下的登录响应（新 accessToken，不含 refreshToken）
     */
    @PostMapping("/tenant/switch")
    Result<LoginResponse> switchTenant(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody SwitchTenantRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse response
    );

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
    Result<Void> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            HttpServletRequest request,
            HttpServletResponse response
    );
}
