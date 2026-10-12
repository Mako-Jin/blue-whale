package com.bluewhale.sts.auth.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 重置口令入参 */
@Schema(description = "重置口令入参")
public class PasswordResetRequest {

    @NotBlank(message = "新口令不能为空")
    // 同 UserSaveRequest：长度下限与字符类别由服务端口令策略判定，这里只挡超长输入
    @Size(max = 128, message = "口令不能超过 128 字")
    @Schema(description = "新口令。长度下限与字符类别由服务端的口令策略判定，故此处不固定 minLength")
    private String password;

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

}
