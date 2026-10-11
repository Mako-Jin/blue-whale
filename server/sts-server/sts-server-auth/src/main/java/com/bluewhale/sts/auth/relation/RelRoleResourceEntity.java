package com.bluewhale.sts.auth.relation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.sts.auth.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 角色-资源关系，对应表 {@code bw_auth_rel_role_resource}。
 *
 * <p>这是 RBAC 的核心授权关系：一行表示"某角色对某资源有某效果"。
 * {@code effect} 允许显式拒绝（{@code DENY}），且 DENY 优先于 ALLOW ——
 * 理由见 {@code GrantEffect}。
 */
@Getter
@Setter
@TableName("bw_auth_rel_role_resource")
public class RelRoleResourceEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long roleId;

    private Long resourceId;

    /** 效果：ALLOW / DENY */
    private String effect;

    /** 优先级，用于多条规则冲突时的裁决 */
    private Integer priority;

    private Long effectiveFrom;

    private Long effectiveTo;

}
