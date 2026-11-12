package com.bluewhale.auth.interfaces.dto.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 内网通讯录用户响应：手机号与邮箱均为脱敏后的值
 *
 * @author BlueWhale
 */
@Data
public class UserDirectoryResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    private String nickname;
    private Long deptId;
    private String deptName;
    /** 脱敏后的手机号 */
    private String phone;
    /** 脱敏后的邮箱 */
    private String email;
    private String avatar;
}
