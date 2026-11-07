package com.bluewhale.oa.collab.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会议纪要。
 *
 * @author BlueWhale
 */
@Data
public class MeetingMinute {

    /** 主键ID */
    private Long id;

    /** 会议ID */
    private Long meetingId;

    /** 纪要内容 */
    private String content;

    /** 纪要作者账号 */
    private String author;

    /** 纪要作者用户ID */
    private Long authorId;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
