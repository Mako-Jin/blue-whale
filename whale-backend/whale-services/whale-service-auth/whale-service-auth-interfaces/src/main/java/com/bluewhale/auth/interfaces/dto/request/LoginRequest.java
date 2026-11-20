package com.bluewhale.auth.interfaces.dto.request;

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

    @NotBlank(message = "{validation.auth.user.username-required}")
    private String username;

    @NotBlank(message = "{validation.auth.user.password-required}")
    private String password;

    /** 验证码（预留） */
    private String captcha;
}
