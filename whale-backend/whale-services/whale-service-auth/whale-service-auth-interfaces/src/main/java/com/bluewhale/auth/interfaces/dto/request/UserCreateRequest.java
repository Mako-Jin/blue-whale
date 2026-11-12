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

    @NotBlank(message = "用户名不能为空")
    @Size(max = 64, message = "用户名长度不能超过 64")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度必须在 6-64 之间")
    private String password;

    private String nickname;
    private String phone;
    private String email;
    private String avatar;
    private Long deptId;
    /** 状态：1 启用 / 0 停用，默认 1 */
    private Integer status;
}
