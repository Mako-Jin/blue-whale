package com.bluewhale.auth.application.model.vo;

import com.bluewhale.auth.domain.aggregate.RoleAggregate;
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
    /** 角色分类：GENERAL 通用 / BUSINESS 业务 / SYSTEM 系统 */
    private String category;
    /** 是否内置角色：内置角色不可删除、不可改名，三员的权限集亦不可手工调整 */
    private Boolean builtIn;
    /** 是否三员之一（系统管理员 / 安全管理员 / 安全审计员），前端据此做互斥置灰 */
    private Boolean threeAdmin;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 由角色聚合根转换为视图对象
     *
     * @param role 角色聚合根
     * @return 角色视图对象
     */
    public static RoleVO from(RoleAggregate role) {
        RoleVO vo = new RoleVO();
        vo.setId(role.getId());
        vo.setRoleCode(role.getRoleCode().value());
        vo.setRoleName(role.getRoleName());
        vo.setDescription(role.getDescription());
        vo.setSortNo(role.getSortNo());
        vo.setStatus(role.getStatus());
        vo.setCategory(role.getCategory());
        vo.setBuiltIn(role.isBuiltIn());
        vo.setThreeAdmin(role.isThreeAdmin());
        vo.setCreateTime(role.getCreateTime());
        vo.setUpdateTime(role.getUpdateTime());
        return vo;
    }
}
