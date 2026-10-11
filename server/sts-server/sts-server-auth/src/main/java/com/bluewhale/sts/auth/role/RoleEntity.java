package com.bluewhale.sts.auth.role;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.sts.auth.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 角色实体，对应表 {@code bw_auth_role}。
 *
 * <p>角色是 RBAC 里"权限的载体"：它不直接属于某个人，而是被授予用户或用户组
 * （见 {@code bw_auth_rel_role_member}），再把资源授予自己
 * （见 {@code bw_auth_rel_role_resource}）。判定"某人能否访问某资源"时，
 * 走的是 用户 → 角色 → 资源 这条链。
 *
 * <p>{@code dataScope} 决定该角色能看到多大范围的数据（数据权限），
 * 取值见 {@code DataScope}；自定义条件的细节放在 {@code filterJson}。
 */
@Getter
@Setter
@TableName("bw_auth_role")
public class RoleEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 角色编码，租户内唯一 */
    private String roleCode;

    private String roleName;

    private Long parentId;

    private Integer roleLevel;

    /** 角色分类：SYSTEM / BUSINESS / CUSTOM */
    private String category;

    /** 是否默认角色（新用户自动授予）：0 否 1 是 */
    private Integer isDefault;

    /** 继承策略：NONE / INHERIT / OVERRIDE */
    private String inheritStrategy;

    /** 数据范围：ALL / DEPT / DEPT_AND_CHILD / SELF / CUSTOM */
    private String dataScope;

    /** 自定义数据范围过滤条件的 JSON */
    private String filterJson;

    private Integer sortNo;

    /** 状态：ENABLED / DISABLED */
    private String status;

    private String description;

}
