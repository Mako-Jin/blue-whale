package com.bluewhale.sts.auth.relation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.sts.auth.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 角色-成员关系，对应表 {@code bw_auth_rel_role_member}。
 *
 * <p>成员可以是用户（{@code USER}）或用户组（{@code GROUP}），由 {@code memberType} 区分。
 * 参照实现里用户与角色之间**没有**独立关联表，一律走多态表，本切片沿用它。
 *
 * <p>{@code effectiveFrom / effectiveTo} 是**时态授权**：授权可以有起止时间
 * （如"临时代理负责人"）。判定时只认当前时间落在区间内的记录，
 * 因此"到期自动失效"不需要定时任务去删数据 —— 定时任务一旦没跑，
 * 权限就会静默地多留一天，而时态过滤不会。
 */
@Getter
@Setter
@TableName("bw_auth_rel_role_member")
public class RelRoleMemberEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long roleId;

    /** 成员主键（用户或用户组，由 memberType 决定） */
    private Long memberId;

    /** 成员类型：USER / GROUP */
    private String memberType;

    private Long effectiveFrom;

    private Long effectiveTo;

}
