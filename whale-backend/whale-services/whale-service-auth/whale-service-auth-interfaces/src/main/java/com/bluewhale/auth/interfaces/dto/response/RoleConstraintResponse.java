package com.bluewhale.auth.interfaces.dto.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 角色约束响应（权限中心扩展 M3）。
 *
 * @author BlueWhale
 */
@Data
public class RoleConstraintResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long tenantId;
    private String constraintCode;
    private String constraintName;
    private String constraintType;
    private List<Long> roleIds;
    private Integer maxAssign;
    private Integer status;
    private String description;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
