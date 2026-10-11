package com.bluewhale.sts.auth.relation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.sts.auth.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 组织-用户关系，对应表 {@code bw_auth_rel_org_user}。
 *
 * <p>一个用户可以同时属于多个组织（多岗位），{@code isPrimaryPosition} 标出主岗。
 * 数据权限里"本部门"取的是主岗所在部门 —— 取全部所属部门会让
 * "本部门"这一档的语义变得含糊（部门多了以后，能看的数据反而超标）。
 */
@Getter
@Setter
@TableName("bw_auth_rel_org_user")
public class RelOrgUserEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long organizationId;

    private Long userId;

    /** 是否主岗：0 否 1 是 */
    private Integer isPrimaryPosition;

    private String positionName;

    /** 关系类型：MEMBER / LEADER ... */
    private String relationType;

    private Long effectiveFrom;

    private Long effectiveTo;

}
