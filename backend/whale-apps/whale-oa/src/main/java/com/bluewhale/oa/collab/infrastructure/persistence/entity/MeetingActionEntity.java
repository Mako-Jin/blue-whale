package com.bluewhale.oa.collab.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 会议行动项实体，对应表 {@code oa_meeting_action}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_meeting_action")
public class MeetingActionEntity extends BaseEntity {

    private Long meetingId;

    private String content;

    private Long ownerId;

    private String ownerName;

    private LocalDate dueDate;

    private String status;

    private Long taskId;
}
