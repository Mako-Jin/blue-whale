package com.bluewhale.sts.auth.authentication;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

/** 当前登录用户信息 */
@Getter
@Setter
public class MeVO {

    private Long userId;

    private String username;

    private String displayName;

    private Long tenantId;

    private String tenantCode;

    @Schema(description = "角色编码集合")
    private Set<String> roles;

    @Schema(description = "权限（资源标识）集合")
    private Set<String> permissions;

    /**
     * 是否需要在下次操作前修改口令。
     *
     * <p><b>为什么由服务端只标记、不拦截：</b>管理员重置口令后置 1，本字段随
     * {@code /api/auth/me} 返回，前端据此把用户引导到改密页。
     * 服务端**不**在这时为登录放行或拒绝做额外动作 —— 直接拒绝登录会形成死锁
     * （进不去就没法改密，而改密接口本身要求已认证）；只发"受限令牌"则要引入
     * 一套令牌能力分级，为一个提示付出的是权限模型的复杂度。
     *
     * <p>本人改密成功后会被清回 0（见 {@code AuthService#changePassword}），
     * 所以前端可以放心地把"这次登录时要不要弹改密页"的判断完全交给本字段。
     */
    @Schema(description = "是否需要在下次操作前修改口令：1 是 0 否（本人改密后即变 0）")
    private Integer mustChangePassword;

}
