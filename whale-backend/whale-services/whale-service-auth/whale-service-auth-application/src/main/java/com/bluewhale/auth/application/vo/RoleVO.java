package com.bluewhale.auth.application.vo;

import com.bluewhale.auth.domain.role.aggregate.Role;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 角色视图对象
 *
 * @author BlueWhale
 */
@Data
public class RoleVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String roleCode;
    private String roleName;
    private String description;
    private Integer sortNo;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 由角色聚合根转换为视图对象
     *
     * @param role 角色聚合根
     * @return 角色视图对象
     */
    public static RoleVO from(Role role) {
        RoleVO vo = new RoleVO();
        vo.setId(role.getId());
        vo.setRoleCode(role.getRoleCode().value());
        vo.setRoleName(role.getRoleName());
        vo.setDescription(role.getDescription());
        vo.setSortNo(role.getSortNo());
        vo.setStatus(role.getStatus());
        vo.setCreateTime(role.getCreateTime());
        vo.setUpdateTime(role.getUpdateTime());
        return vo;
    }
}
