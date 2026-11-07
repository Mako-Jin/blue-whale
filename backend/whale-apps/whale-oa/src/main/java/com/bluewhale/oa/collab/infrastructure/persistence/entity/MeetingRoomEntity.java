package com.bluewhale.oa.collab.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 会议室实体，对应表 {@code oa_meeting_room}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_meeting_room")
public class MeetingRoomEntity extends BaseEntity {

    private String name;

    private String location;

    private Integer capacity;

    private String status;

    private String remark;
}
