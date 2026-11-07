package com.bluewhale.oa.collab.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 会议参会人实体，对应表 {@code oa_meeting_participant}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_meeting_participant")
public class MeetingParticipantEntity extends BaseEntity {

    private Long meetingId;

    private Long userId;

    private String userName;

    private Integer required;

    private String status;

    private String remark;
}
