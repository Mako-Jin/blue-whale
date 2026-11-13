package com.bluewhale.auth.interfaces.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 三方登录码换令牌请求（权限中心扩展 M2）。
 *
 * @author BlueWhale
 */
@Data
public class SocialLoginByCodeRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 一次性登录码（回调 302 携带给前端，短时效、防重放） */
    @NotBlank(message = "登录码不能为空")
    private String loginCode;
}
