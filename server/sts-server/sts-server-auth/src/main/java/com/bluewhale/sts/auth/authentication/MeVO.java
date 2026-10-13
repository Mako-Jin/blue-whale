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
     * <p><b>为什么由服务端只标记、不拦截：</b>管理员重置口令后置 1，前端据此把用户
     * 引导到改密页（入口在基座，取舍见 {@code docs/decisions/0044}）。
     * 服务端**不**在这时为登录放行或拒绝做额外动作 —— 直接拒绝登录会形成死锁
     * （进不去就没法改密，而改密接口本身要求已认证）；只发"受限令牌"则要引入
     * 一套令牌能力分级，为一个提示付出的是权限模型的复杂度。
     *
     * <p><b>它不是本接口独有的字段：</b>{@link LoginVO} 上也有一份同名字段，因为
     * 前端基座走的是**登录 / 刷新**那条路，而 {@code /api/auth/me} 只在
     * "后台改了权限之后重新拉一次身份"时被调用。只让本接口返回它，
     * 等于让它永远到不了用户面前。三处取值同源，都来自
     * {@code bw_auth_user.must_change_password} 一列。
     *
     * <p>本人改密成功后会被清回 0（见 {@code AuthService#changePassword}），
     * 所以前端可以放心地把"这次登录时要不要引导改密页"的判断完全交给本字段。
     */
    @Schema(description = "是否需要在下次操作前修改口令：1 是 0 否（本人改密后即变 0）")
    private Integer mustChangePassword;

}
