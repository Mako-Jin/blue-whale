package com.bluewhale.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求
 *
 * @author BlueWhale
 */
@Data
public class LoginRequest {

    /** 租户编码（可空；为空时使用默认租户 default，兼容存量单租户客户端） */
    private String tenantCode;

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    /** 验证码（预留） */
    private String captcha;
}
