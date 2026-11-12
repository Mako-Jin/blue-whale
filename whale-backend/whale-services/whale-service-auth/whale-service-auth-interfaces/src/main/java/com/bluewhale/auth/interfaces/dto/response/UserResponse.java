package com.bluewhale.auth.interfaces.dto.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户响应（不含口令字段，杜绝密码泄露）
 *
 * @author BlueWhale
 */
@Data
public class UserResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    private String nickname;
    private String phone;
    private String email;
    private String avatar;
    private Long deptId;
    private Integer status;
    /** 是否强制首登改密（REQ-SEC-023） */
    private Boolean mustChangePassword;
    /** 最近一次改密时间（REQ-SEC-023：管理端据此判断口令年龄） */
    private LocalDateTime passwordUpdatedTime;
    private LocalDateTime lastLoginTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
