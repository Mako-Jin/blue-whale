package com.bluewhale.party.activity.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 组织生活实体，对应表 {@code pty_activity}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pty_activity")
public class PartyActivityEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 所属支部 ID */
    private Long branchId;

    /** 组织生活类型 */
    private String activityType;

    /** 活动主题 */
    private String title;

    /** 计划日期 */
    private LocalDate plannedDate;

    /** 实际召开日期 */
    private LocalDate heldDate;

    /** 活动地点 */
    private String location;

    /** 主持人 */
    private String hostName;

    /** 应到人数 */
    private Integer expectedCount;

    /** 状态 */
    private String status;

    /** 会议纪要 */
    private String summary;

    /** 备注 */
    private String remark;
}
