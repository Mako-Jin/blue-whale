package com.bluewhale.sts.auth.usergroup;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.sts.auth.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户组实体，对应表 {@code bw_auth_user_group}。
 *
 * <p><b>用户组与角色的区别：</b>角色回答"能做什么"，用户组回答"是谁"。
 * 二者都能作为成员被授予角色，但用途不同：
 * 用户组随组织形态变化（如"华东大区"、"财务部"），角色随职责变化（如"审批人"）。
 * 把两者混为一谈，会导致"组织调整"和"权限调整"互相牵连。
 *
 * <p>{@code category} 区分静态组（成员手工维护）与动态组（成员由 {@code filterJson} 规则算出）。
 * 本切片只落地静态组的存储，动态组的规则求值在后续切片补齐。
 */
@Getter
@Setter
@TableName("bw_auth_user_group")
public class UserGroupEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 用户组编码，租户内唯一 */
    private String groupCode;

    private String groupName;

    private Long parentId;

    /** 分组方式：STATIC / DYNAMIC */
    private String category;

    /** 动态成员的过滤条件 JSON */
    private String filterJson;

    /** 组织范围限制的 JSON */
    private String orgScopeJson;

    /** 是否默认用户组：0 否 1 是 */
    private Integer isDefault;

    /** 是否启用：0 否 1 是 */
    private Integer isEnabled;

    private Integer sortNo;

    private String description;

}
