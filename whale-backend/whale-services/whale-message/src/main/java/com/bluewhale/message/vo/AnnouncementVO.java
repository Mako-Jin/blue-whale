package com.bluewhale.message.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统公告视图。
 *
 * @author BlueWhale
 */
@Data
public class AnnouncementVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 公告ID */
    private Long id;

    /** 消息ID（msg_record.id） */
    private Long recordId;

    /** 标题 */
    private String title;

    /** 正文 */
    private String content;

    /** 业务类型 */
    private String bizType;

    /** 公告状态：PUBLISHED / REVOKED */
    private String status;

    /** 发布人用户ID */
    private Long publisherId;

    /** 发布时间 */
    private LocalDateTime publishTime;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间（撤回操作会刷新，可据此展示撤回时间） */
    private LocalDateTime updateTime;
}
