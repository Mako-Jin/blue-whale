package com.bluewhale.oa.calendar.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 日程共享记录。
 *
 * @author BlueWhale
 */
@Data
public class CalendarShare {

    /** 主键ID */
    private Long id;

    /** 日程ID */
    private Long calendarId;

    /** 被共享用户ID */
    private Long userId;

    /** 被共享用户姓名快照 */
    private String userName;

    /** 创建时间 */
    private LocalDateTime createTime;
}
