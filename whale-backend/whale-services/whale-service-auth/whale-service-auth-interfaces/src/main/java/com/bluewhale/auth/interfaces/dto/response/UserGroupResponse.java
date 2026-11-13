package com.bluewhale.auth.interfaces.dto.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户组响应（权限中心扩展 M3）。
 *
 * @author BlueWhale
 */
@Data
public class UserGroupResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long tenantId;
    private String groupCode;
    private String groupName;
    private Long parentId;
    private String category;
    private String filtersJson;
    private String description;
    private Integer sortNo;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
