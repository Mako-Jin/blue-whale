package com.bluewhale.pm.interfaces.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工作项评论视图对象
 *
 * @author BlueWhale
 */
@Data
public class WorkItemCommentVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private String id;

    /** 工作项ID */
    private String workItemId;

    /** 评论人用户ID */
    private String authorId;

    /** 评论内容 */
    private String content;

    /** 父评论ID */
    private String parentId;

    /** 创建时间 */
    private LocalDateTime createTime;
}
