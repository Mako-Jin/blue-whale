package com.bluewhale.auth.application.model.vo;

import com.bluewhale.auth.domain.permission.aggregate.Permission;
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

    /**
     * 由权限点聚合根转换为视图对象
     *
     * @param permission 权限点聚合根
     * @return 权限点视图对象
     */
    public static PermissionVO from(Permission permission) {
        PermissionVO vo = new PermissionVO();
        vo.setId(permission.getId());
        vo.setParentId(permission.getParentId());
        vo.setPermCode(permission.getPermCode().value());
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
