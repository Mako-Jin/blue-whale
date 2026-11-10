package com.bluewhale.oa.collab.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 会议实体，对应表 {@code oa_meeting}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_meeting")
public class MeetingEntity extends BaseEntity {

    private String code;

    private String title;

    private Long roomId;

    private String organizer;

    private Long organizerId;

    private Long deptId;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String agenda;

    private String status;

    private String remark;
}
