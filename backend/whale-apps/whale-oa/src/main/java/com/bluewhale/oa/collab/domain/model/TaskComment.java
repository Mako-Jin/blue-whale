package com.bluewhale.oa.collab.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务评论。
 *
 * @author BlueWhale
 */
@Data
public class TaskComment {

    /** 主键ID */
    private Long id;

    /** 任务ID */
    private Long taskId;

    /** 评论人用户ID */
    private Long userId;

    /** 评论人姓名快照 */
    private String userName;

    /** 评论内容 */
    private String content;

    /** 创建时间 */
    private LocalDateTime createTime;
}
