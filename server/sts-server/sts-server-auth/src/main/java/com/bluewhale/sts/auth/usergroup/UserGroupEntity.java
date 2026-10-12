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
 * **当前只支持静态组**：动态组的规则求值没有实现，接口会显式拒绝
 * {@code DYNAMIC} —— 接受一个永远不会算出成员的规则，等于交付一个假功能。
 * 同理 {@code filterJson} / {@code orgScopeJson} 两列**不在接口入参里**：
 * 它们的消费方（动态成员求值、组织范围过滤）都还不存在，
 * 现在写进去只会是"配置了、没人读"。
 *
 * <p><b>{@code parentId} 只是一列组织信息，不产生任何权限。</b>
 * 组与组的层级不参与判定：判定链路是"用户 → 它所属的组 → 组的角色"，
 * 每次只跳一层（见 {@code MemberType} 的注释）。把一个组挂到另一个组下面，
 * 不会让子组成员继承父组的角色。列保留下来是为了列表的分组展示，
 * 与 {@code bw_auth_role.parent_id} 是同一种处理 —— 那边也不产生权限（角色继承未实现）。
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

    /** 分组方式：STATIC（手工维护成员）。DYNAMIC 未实现，接口会拒绝 */
    private String category;

    /** 动态成员的过滤条件 JSON。**当前不在接口入参里**：动态组未实现，没有消费方 */
    private String filterJson;

    /** 组织范围限制的 JSON。**当前不在接口入参里**：没有消费方 */
    private String orgScopeJson;

    /** 是否默认用户组：0 否 1 是 */
    private Integer isDefault;

    /** 是否启用：0 否 1 是 */
    private Integer isEnabled;

    private Integer sortNo;

    private String description;

}
