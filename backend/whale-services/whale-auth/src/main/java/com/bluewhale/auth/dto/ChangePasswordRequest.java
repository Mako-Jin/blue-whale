package com.bluewhale.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 重置用户密码请求（管理端操作，仅需新口令）
 *
 * @author BlueWhale
 */
@Data
public class ChangePasswordRequest {

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度必须在 6-64 之间")
    private String password;
}
