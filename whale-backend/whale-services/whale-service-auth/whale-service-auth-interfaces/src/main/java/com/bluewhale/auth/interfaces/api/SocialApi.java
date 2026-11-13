package com.bluewhale.auth.interfaces.api;

import com.bluewhale.auth.interfaces.dto.request.SocialBindAuthorizeRequest;
import com.bluewhale.auth.interfaces.dto.request.SocialLoginByCodeRequest;
import com.bluewhale.auth.interfaces.dto.response.LoginResponse;
import com.bluewhale.auth.interfaces.dto.response.SocialAuthorizeResponse;
import com.bluewhale.auth.interfaces.dto.response.SocialBindingResponse;
import com.bluewhale.common.core.result.Result;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * 三方登录 API 契约（权限中心扩展 M2：钉钉 / 飞书 / 企业微信）。
 *
 * <p>授权起始与渠道回调走浏览器跳转链路（免登录）；一次性登录码换令牌为短时效
 * 凭证交换（免登录、防重放）；绑定/解绑/绑定列表须已登录。渠道回调端点返回 302
 * 重定向回前端回调页（{@code code=一次性登录码} 或 {@code error=失败标记}）。</p>
 *
 * @author BlueWhale
 */
@RequestMapping(path = "/social/v{version}", version = "1")
public interface SocialApi {

    /**
     * 登录意图：生成渠道授权页 URL（免登录）
     *
     * @param provider 渠道编码（dingtalk/feishu/wecom）
     * @param tenantCode 登录目标租户编码
     * @param redirect 登录完成后的前端回跳相对路径（以 / 开头）
     * @return 授权页 URL
     */
    @GetMapping("/{provider}/authorize")
    Result<SocialAuthorizeResponse> authorize(@PathVariable String provider,
                                              @RequestParam String tenantCode,
                                              @RequestParam String redirect);

    /**
     * 渠道回调（免登录，浏览器导航）：裁决后 302 回前端回调页
     *
     * @param provider 渠道编码
     * @param code 渠道授权码（用户拒绝授权时缺失）
     * @param state 发起授权时的防伪随机串
     * @return 302 重定向（无响应体）
     */
    @GetMapping("/{provider}/callback")
    ResponseEntity<Void> callback(@PathVariable String provider,
                                  @RequestParam(required = false) String code,
                                  @RequestParam String state);

    /**
     * 一次性登录码换令牌（免登录；码一次性、短时效、防重放）
     *
     * @param request 换令牌请求
     * @return 登录结果（refreshToken 由接口层写 HttpOnly Cookie，不落响应体）
     */
    @PostMapping("/login")
    Result<LoginResponse> loginByCode(@Valid @RequestBody SocialLoginByCodeRequest request,
                                      HttpServletResponse response);

    /**
     * 绑定意图：为当前登录用户生成渠道授权页 URL（须已登录）
     *
     * @param request 绑定授权起始请求
     * @return 授权页 URL
     */
    @PostMapping("/bind/authorize")
    Result<SocialAuthorizeResponse> bindAuthorize(@Valid @RequestBody SocialBindAuthorizeRequest request);

    /**
     * 解绑当前用户的指定渠道绑定（须已登录；解绑后该三方身份不再可登录）
     *
     * @param provider 渠道编码
     * @return 操作结果
     */
    @DeleteMapping("/bind/{provider}")
    Result<Void> unbind(@PathVariable String provider);

    /**
     * 当前用户的有效三方绑定列表（须已登录）
     *
     * @return 绑定列表（按绑定时间升序）
     */
    @GetMapping("/bindings")
    Result<List<SocialBindingResponse>> bindings();
}
