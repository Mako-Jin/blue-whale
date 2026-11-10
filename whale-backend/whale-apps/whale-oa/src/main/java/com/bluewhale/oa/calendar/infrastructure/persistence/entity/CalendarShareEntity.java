package com.bluewhale.oa.calendar.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 日程共享实体，对应表 {@code oa_calendar_share}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_calendar_share")
public class CalendarShareEntity extends BaseEntity {

    private Long calendarId;

    private Long userId;

    private String userName;
}
