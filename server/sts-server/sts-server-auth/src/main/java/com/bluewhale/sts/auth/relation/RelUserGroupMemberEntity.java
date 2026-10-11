package com.bluewhale.sts.auth.relation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.sts.auth.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户组-成员关系，对应表 {@code bw_auth_rel_user_group_member}。
 *
 * <p>与角色-成员一样是多态表：成员可以是用户，也可以是另一个用户组（组嵌套）。
 * 组嵌套让"华东大区"可以包含"上海分部""杭州分部"，而不必把成员平铺一遍。
 */
@Getter
@Setter
@TableName("bw_auth_rel_user_group_member")
public class RelUserGroupMemberEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long userGroupId;

    private Long memberId;

    /** 成员类型：USER / GROUP */
    private String memberType;

    private Long effectiveFrom;

    private Long effectiveTo;

}
