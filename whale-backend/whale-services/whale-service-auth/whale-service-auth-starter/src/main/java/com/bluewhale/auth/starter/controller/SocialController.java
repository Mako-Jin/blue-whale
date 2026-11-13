package com.bluewhale.auth.starter.controller;

import com.bluewhale.auth.application.model.command.SocialAuthorizeCommand;
import com.bluewhale.auth.application.model.command.SocialBindAuthorizeCommand;
import com.bluewhale.auth.application.model.command.SocialCallbackCommand;
import com.bluewhale.auth.application.model.command.SocialLoginByCodeCommand;
import com.bluewhale.auth.application.model.command.SocialUnbindCommand;
import com.bluewhale.auth.application.model.result.LoginResult;
import com.bluewhale.auth.application.model.result.SocialAuthorizeResult;
import com.bluewhale.auth.application.model.result.SocialBindingResult;
import com.bluewhale.auth.application.model.result.SocialCallbackResult;
import com.bluewhale.auth.application.service.SocialLoginAppService;
import com.bluewhale.auth.interfaces.api.SocialApi;
import com.bluewhale.auth.interfaces.dto.request.SocialBindAuthorizeRequest;
import com.bluewhale.auth.interfaces.dto.request.SocialLoginByCodeRequest;
import com.bluewhale.auth.interfaces.dto.response.LoginResponse;
import com.bluewhale.auth.interfaces.dto.response.SocialAuthorizeResponse;
import com.bluewhale.auth.interfaces.dto.response.SocialBindingResponse;
import com.bluewhale.auth.interfaces.web.AuthCookieManager;
import com.bluewhale.auth.starter.assemble.AuthAssembler;
import com.bluewhale.common.core.result.Result;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * 三方登录 Web 适配层（权限中心扩展 M2）：实现 {@link SocialApi} 契约。
 *
 * <p>渠道回调以 302 重定向回前端回调页（{@code code=一次性登录码} 或 {@code error=失败标记}），
 * 前端再以 {@code POST /social/v1/login} 换令牌（refreshToken 走 HttpOnly Cookie）。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequiredArgsConstructor
public class SocialController implements SocialApi {

    private final SocialLoginAppService socialLoginAppService;
    private final AuthCookieManager cookieManager;
    private final AuthAssembler authAssembler;

    /**
     * 登录意图：生成渠道授权页 URL
     *
     * @param provider 渠道编码
     * @param tenantCode 目标租户编码
     * @param redirect 前端回跳相对路径
     * @return 授权页 URL
     */
    @Override
    public Result<SocialAuthorizeResponse> authorize(String provider, String tenantCode, String redirect) {
        SocialAuthorizeResult result = socialLoginAppService.startLogin(
                new SocialAuthorizeCommand(provider, tenantCode, redirect));
        return Result.ok(new SocialAuthorizeResponse(result.authorizeUrl(), result.state()));
    }

    /**
     * 渠道回调：302 重定向回前端回调页（成功带一次性登录码，失败带错误标记）
     *
     * @param provider 渠道编码
     * @param code 渠道授权码
     * @param state 防伪随机串
     * @return 302 重定向响应
     */
    @Override
    public ResponseEntity<Void> callback(String provider, String code, String state) {
        SocialCallbackResult result = socialLoginAppService.completeCallback(
                new SocialCallbackCommand(provider, code, state));
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(result.redirectUrl()));
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

    /**
     * 一次性登录码换令牌：成功后下发 refresh/CSRF Cookie
     *
     * @param request 换令牌请求
     * @param response 当前 HTTP 响应（写入 Set-Cookie）
     * @return 登录响应
     */
    @Override
    public Result<LoginResponse> loginByCode(SocialLoginByCodeRequest request, HttpServletResponse response) {
        LoginResult result = socialLoginAppService.exchangeLoginCode(
                new SocialLoginByCodeCommand(request.getLoginCode()));
        cookieManager.writeAuthCookies(response, result.refreshToken());
        return Result.ok(authAssembler.toLoginResponse(result));
    }

    /**
     * 绑定意图：为当前登录用户生成渠道授权页 URL
     *
     * @param request 绑定授权起始请求
     * @return 授权页 URL
     */
    @Override
    public Result<SocialAuthorizeResponse> bindAuthorize(SocialBindAuthorizeRequest request) {
        SocialAuthorizeResult result = socialLoginAppService.startBind(
                new SocialBindAuthorizeCommand(request.getProvider(), request.getRedirect()));
        return Result.ok(new SocialAuthorizeResponse(result.authorizeUrl(), result.state()));
    }

    /**
     * 解绑当前用户的指定渠道绑定
     *
     * @param provider 渠道编码
     * @return 操作结果
     */
    @Override
    public Result<Void> unbind(String provider) {
        socialLoginAppService.unbind(new SocialUnbindCommand(provider));
        return Result.ok();
    }

    /**
     * 当前用户的有效三方绑定列表
     *
     * @return 绑定列表
     */
    @Override
    public Result<List<SocialBindingResponse>> bindings() {
        List<SocialBindingResult> results = socialLoginAppService.listBindings();
        return Result.ok(results.stream()
                .map(item -> SocialBindingResponse.of(item.provider(), item.providerName(),
                        item.nickname(), item.avatar(), item.boundTime()))
                .toList());
    }
}
