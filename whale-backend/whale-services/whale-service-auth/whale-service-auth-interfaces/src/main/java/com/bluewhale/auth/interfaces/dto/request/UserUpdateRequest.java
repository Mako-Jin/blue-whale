package com.bluewhale.auth.interfaces.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新用户请求（用户名不可修改；password 传空则不修改）
 *
 * @author BlueWhale
 */
@Data
public class UserUpdateRequest {

    @Size(min = 6, max = 64, message = "密码长度必须在 6-64 之间")
    private String password;

    private String nickname;
    private String phone;
    private String email;
    private String avatar;
    private Long deptId;
    private Integer status;
}
