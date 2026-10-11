package com.bluewhale.sts.auth.authentication;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

/**
 * 登录 / 刷新成功后的返回体。
 *
 * <p>令牌与身份信息一起返回：前端拿到后即可渲染导航与按钮，
 * 不必再发一次"查我的信息"请求 —— 少一次往返，也少一次"令牌有了但用户信息还没到"的中间态。
 */
@Getter
@Setter
public class LoginVO {

    @Schema(description = "访问令牌，后续请求放在 Authorization: Bearer <token>")
    private String accessToken;

    @Schema(description = "刷新令牌，用于换取新的访问令牌")
    private String refreshToken;

    @Schema(description = "令牌类型，固定 Bearer")
    private String tokenType = "Bearer";

    @Schema(description = "访问令牌有效期（秒）")
    private long expiresIn;

    private Long userId;

    private String username;

    private String displayName;

    private Long tenantId;

    private String tenantCode;

    @Schema(description = "角色编码集合")
    private Set<String> roles;

    @Schema(description = "权限（资源标识）集合")
    private Set<String> permissions;

}
