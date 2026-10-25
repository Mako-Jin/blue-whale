package com.bluewhale.pm.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工作项评论实体，对应表 {@code pm_comment}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pm_comment")
public class PmComment extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 项目ID */
    private Long projectId;

    /** 工作项ID */
    private Long workItemId;

    /** 评论人用户ID */
    private Long authorId;

    /** 评论内容 */
    private String content;

    /** 父评论ID */
    private Long parentId;
}
