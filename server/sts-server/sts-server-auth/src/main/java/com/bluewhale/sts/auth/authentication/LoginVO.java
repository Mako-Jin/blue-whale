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

    /**
     * 是否需要在下次操作前修改口令，取值含义同 {@link MeVO#getMustChangePassword()}。
     *
     * <p><b>为什么登录响应也必须带它：</b>本字段的消费者是前端基座的引导弹窗，
     * 而前端走的是**登录 / 刷新**这条路 —— 它只在"后台改了权限之后"才调一次
     * {@code /api/auth/me}（见 {@code common-api} 的 {@code AuthChannel.reload}）。
     * 因此若只让 {@code /me} 返回它，实际效果是**前端永远看不见**：
     * 登录时拿不到、刷新页面走刷新接口也拿不到，这一列会退化成它此前那种
     * "字段齐全、无人读取"的状态。
     *
     * <p>刷新这条尤其不能少：不带着它，用户刷新一次浏览器，"待改口令"的引导就消失了。
     * 同一个事实在三个出口取值，源头都只有 {@code bw_auth_user.must_change_password} 一列。
     */
    @Schema(description = "是否需要在下次操作前修改口令：1 是 0 否（本人改密后即变 0）")
    private Integer mustChangePassword;

}
