package com.bluewhale.oa.collab.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 会议纪要实体，对应表 {@code oa_meeting_minute}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_meeting_minute")
public class MeetingMinuteEntity extends BaseEntity {

    private Long meetingId;

    private String content;

    private String author;

    private Long authorId;
}
