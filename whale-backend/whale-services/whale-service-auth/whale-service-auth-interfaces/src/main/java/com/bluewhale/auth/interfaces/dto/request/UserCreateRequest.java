package com.bluewhale.auth.interfaces.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增用户请求
 *
 * @author BlueWhale
 */
@Data
public class UserCreateRequest {

    @NotBlank(message = "{validation.auth.user.username-required}")
    @Size(max = 64, message = "{validation.auth.user.username-size}")
    private String username;

    @NotBlank(message = "{validation.auth.user.password-required}")
    @Size(min = 6, max = 64, message = "{validation.auth.user.password-size}")
    private String password;

    private String nickname;
    private String phone;
    private String email;
    private String avatar;
    private Long deptId;
    /** 状态：1 启用 / 0 停用，默认 1 */
    private Integer status;
}
