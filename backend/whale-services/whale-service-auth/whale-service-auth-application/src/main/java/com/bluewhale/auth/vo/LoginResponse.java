package com.bluewhale.auth.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 登录 / 刷新 Token 响应
 *
 * @author BlueWhale
 */
@Data
public class LoginResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private String accessToken;
    private String refreshToken;
    /** 固定 Bearer，请求头使用：Authorization: Bearer {@code accessToken} */
    private String tokenType;
    /** accessToken 有效期（秒） */
    private long expiresIn;
    /** 所属租户 ID（Wave 5.54：令牌已绑定租户） */
    private Long tenantId;
    private String username;
    private String nickname;
    /** 角色 code 列表 */
    private List<String> roles;
}
