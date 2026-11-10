package com.bluewhale.auth.application.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 登录 / 刷新 Token 响应（V-03：refreshToken 已迁移 HttpOnly Cookie，不再进入响应体）
 *
 * @author BlueWhale
 */
@Data
public class LoginResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private String accessToken;
    /** 固定 Bearer，请求头使用：Authorization: Bearer {@code accessToken} */
    private String tokenType;
    /** accessToken 有效期（秒） */
    private long expiresIn;
    /** 用户 ID（T-03 前端强制改密弹窗依赖该字段提交 PUT /user/{id}/password） */
    private Long userId;
    /** 所属租户 ID（Wave 5.54：令牌已绑定租户） */
    private Long tenantId;
    private String username;
    private String nickname;
    /** 角色 code 列表 */
    private List<String> roles;
    /** 是否强制首登改密（T-03：true 时前端应引导用户先修改口令） */
    private boolean mustChangePassword;
    /** 口令是否已到期（REQ-SEC-023：到期会在登录/刷新时强制改密） */
    private boolean passwordExpired;
    /** 距口令到期剩余天数；不启用有效期时返回 -1 */
    private long passwordExpiresInDays = -1L;
}
