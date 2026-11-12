package com.bluewhale.auth.interfaces.dto.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 人员生命周期执行结果：仅入职建号时返回一次性临时口令
 *
 * @author BlueWhale
 */
@Data
public class UserLifecycleResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private String username;
    /** 账号状态（1 启用 / 0 停用） */
    private Integer status;
    private Long deptId;
    /** 一次性临时口令（仅入职建号时返回） */
    private String temporaryPassword;
}
