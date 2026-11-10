package com.bluewhale.oa.calendar.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 日程实体，对应表 {@code oa_calendar}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_calendar")
public class CalendarEntryEntity extends BaseEntity {

    private Long ownerId;

    private String ownerName;

    private String title;

    private String description;

    private String location;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer allDay;

    private String visibility;

    private Integer remindMinutes;

    private String source;

    private Long sourceId;
}
