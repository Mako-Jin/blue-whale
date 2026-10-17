package com.bluewhale.auth.dto;

import lombok.Data;

/**
 * 登出请求。
 * <p>{@code refreshToken} 为可选字段：客户端若能携带，登出时它与请求头中的 accessToken
 * 一并写入黑名单，使 refreshToken 也立即失效（REQ-SEC-004）；不携带则仅失效 accessToken。</p>
 *
 * @author BlueWhale
 */
@Data
public class LogoutRequest {

    /** 待一并失效的 refreshToken（可空） */
    private String refreshToken;
}
