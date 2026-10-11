package com.bluewhale.sts.auth.authentication;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 刷新令牌入参 */
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
