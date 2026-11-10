package com.bluewhale.auth.vo;

import lombok.Getter;

/**
 * 令牌签发结果（登录 / 刷新 / 切换租户共用，V-03）。
 *
 * <p>{@code response} 为响应体载荷（accessToken、有效期与用户基础信息，不含 refreshToken）；
 * {@code refreshToken} 仅供接口层写入 HttpOnly Cookie，绝不下发响应体。</p>
 *
 * @author BlueWhale
 */
@Getter
public class AuthTokenIssue {

    /** 响应体载荷（accessToken + 用户基础信息） */
    private final LoginResponse response;

    /** 新签发的 refreshToken，仅用于写入 HttpOnly Cookie（响应体不可见） */
    private final String refreshToken;

    /**
     * 构造令牌签发结果
     *
     * @param response 响应体载荷
     * @param refreshToken 新签发的 refreshToken（仅写 Cookie）
     */
    public AuthTokenIssue(LoginResponse response, String refreshToken) {
        this.response = response;
        this.refreshToken = refreshToken;
    }
}
