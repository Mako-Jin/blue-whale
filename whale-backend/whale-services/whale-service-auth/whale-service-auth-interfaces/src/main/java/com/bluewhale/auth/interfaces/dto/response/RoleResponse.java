package com.bluewhale.auth.interfaces.dto.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 角色响应
 *
 * @author BlueWhale
 */
@Data
public class RoleResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String roleCode;
    private String roleName;
    private String description;
    private Integer sortNo;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
