package com.bluewhale.party.union.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 工会活动报名实体，对应表 {@code pty_union_signup}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pty_union_signup")
public class UnionSignupEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 活动 ID */
    @TableField("activity_id")
    private Long activityId;

    /** 工会会员 ID */
    @TableField("union_member_id")
    private Long unionMemberId;

    /** 报名用户 ID */
    private Long userId;

    /** 报名状态 */
    private String status;

    /** 报名时间 */
    private LocalDateTime signupAt;

    /** 取消时间 */
    private LocalDateTime cancelledAt;
}
