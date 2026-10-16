package com.bluewhale.auth.vo;

import com.bluewhale.auth.entity.SysPermission;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 权限点视图对象
 *
 * @author BlueWhale
 */
@Data
public class PermissionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long parentId;
    private String permCode;
    private String permName;
    private String permType;
    private String path;
    private String method;
    private Integer sortNo;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static PermissionVO from(SysPermission permission) {
        PermissionVO vo = new PermissionVO();
        vo.setId(permission.getId());
        vo.setParentId(permission.getParentId());
        vo.setPermCode(permission.getPermCode());
        vo.setPermName(permission.getPermName());
        vo.setPermType(permission.getPermType());
        vo.setPath(permission.getPath());
        vo.setMethod(permission.getMethod());
        vo.setSortNo(permission.getSortNo());
        vo.setStatus(permission.getStatus());
        vo.setCreateTime(permission.getCreateTime());
        vo.setUpdateTime(permission.getUpdateTime());
        return vo;
    }
}
