package com.bluewhale.party.activity.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 组织生活签到实体，对应表 {@code pty_activity_signin}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pty_activity_signin")
public class ActivitySigninEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 组织生活 ID */
    private Long activityId;

    /** 党员档案 ID */
    private Long memberId;

    /** 党员姓名快照 */
    private String memberName;

    /** 是否到会：1 到会 / 0 缺席 */
    private Integer present;

    /** 缺席原因 */
    private String absentReason;

    /** 签到时间 */
    private LocalDateTime signedAt;
}
