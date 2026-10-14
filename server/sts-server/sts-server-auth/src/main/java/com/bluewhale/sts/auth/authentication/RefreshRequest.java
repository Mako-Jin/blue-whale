package com.bluewhale.sts.auth.authentication;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 刷新令牌入参 —— 刷新（{@code /api/auth/refresh}）与登出（{@code /api/auth/logout}）共用。
 *
 * <p>两者都只需要"那一个刷新令牌"作为主语：刷新是把它换成一对新令牌，
 * 登出是把它作废。字段一样，问的问题也一样（"你说的是哪一个会话"）。
 *
 * <p>{@code @NotBlank} 在登出这条路上曾经**没起作用** —— 控制器当时把请求体标成可选，
 * 于是"不带令牌"被当成另一个语义（撤销全部）。现在两条语义各占一条路径，
 * 这个校验才真正开始拦东西（见 {@code docs/decisions/0047}）。
 */
public class RefreshRequest {

    @NotBlank(message = "刷新令牌不能为空")
    @Schema(description = "刷新令牌")
    private String refreshToken;

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

}
