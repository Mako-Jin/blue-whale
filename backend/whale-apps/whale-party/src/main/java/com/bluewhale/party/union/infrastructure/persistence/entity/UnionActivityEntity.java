package com.bluewhale.party.union.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 工会活动实体，对应表 {@code pty_union_activity}。
 *
 * <p>时间与编号列显式映射：活动时间参与「未开始才可报名」的判定，
 * 列名错位会让判定读到空值并误放行。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pty_union_activity")
public class UnionActivityEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 活动编号（租户内唯一） */
    @TableField("activity_no")
    private String activityNo;

    /** 活动名称 */
    private String title;

    /** 活动类型 */
    private String activityType;

    /** 开始时间 */
    @TableField("start_time")
    private LocalDateTime startTime;

    /** 结束时间 */
    @TableField("end_time")
    private LocalDateTime endTime;

    /** 活动地点 */
    private String location;

    /** 名额上限，0 表示不限 */
    private Integer quota;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;
}
