package com.bluewhale.oa.collab.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公告接收人快照。
 *
 * @author BlueWhale
 */
@Data
public class NoticeRecipient {

    /** 主键ID */
    private Long id;

    /** 公告ID */
    private Long noticeId;

    /** 接收人用户ID */
    private Long userId;

    /** 接收人姓名快照 */
    private String userName;

    /** 是否已读：0 未读 / 1 已读 */
    private Integer readFlag;

    /** 阅读时间 */
    private LocalDateTime readTime;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
