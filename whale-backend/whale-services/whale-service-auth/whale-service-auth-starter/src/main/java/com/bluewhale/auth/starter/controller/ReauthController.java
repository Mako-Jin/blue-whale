package com.bluewhale.auth.starter.controller;

import com.bluewhale.auth.application.security.ReauthAppService;
import com.bluewhale.auth.application.security.ReauthResult;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 二次认证端点（Wave 6.22）
 *
 * <p>路径与 {@code AuthApi} 同为版本化前缀 {@code /auth/v{version}}（{@code PathApiVersionResolver}
 * 解析首段，实际路径 {@code /auth/v1/reauth}）。要求登录态，不进网关白名单与
 * {@code SecurityConfig.permitAll}（URL 版本化三处同步红线：本端点零改动）。</p>
 *
 * <p>本端点自身不标注 {@code @RequireReauth}（否则无法完成首次验密），但受登录态约束。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/auth/v{version}", version = "1")
@RequiredArgsConstructor
public class ReauthController {

    /** 二次认证应用服务 */
    private final ReauthAppService reauthAppService;

    /**
     * 校验当前用户口令并发放 step-up 凭证
     *
     * @param request 验密请求（当前用户明文口令）
     * @return 凭证与有效期
     */
    @PostMapping("/reauth")
    @AuditLog(operation = "二次认证", type = OperateType.UPDATE, targetType = "sys_user")
    public Result<ReauthResponse> reauth(@Valid @RequestBody ReauthRequest request) {
        ReauthResult result = reauthAppService.reauthenticate(request.password());
        return Result.ok(new ReauthResponse(result.token(), result.expiresInSeconds()));
    }

    /**
     * 验密请求
     *
     * @param password 当前登录用户的明文口令
     * @author BlueWhale
     */
    public record ReauthRequest(@NotBlank(message = "请输入当前密码") String password) {
    }

    /**
     * 验密响应
     *
     * @param token step-up 凭证（请放入 {@code X-Reauth-Token} 请求头）
     * @param expiresIn 有效期秒数
     * @author BlueWhale
     */
    public record ReauthResponse(String token, long expiresIn) {
    }
}
