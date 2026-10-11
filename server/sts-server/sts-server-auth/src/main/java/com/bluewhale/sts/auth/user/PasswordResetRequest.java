package com.bluewhale.sts.auth.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 重置口令入参 */
@Schema(description = "重置口令入参")
public class PasswordResetRequest {

    @NotBlank(message = "新口令不能为空")
    @Size(min = 8, max = 128, message = "口令长度须在 8~128 之间")
    @Schema(description = "新口令")
    private String password;

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

}
