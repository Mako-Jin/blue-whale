package com.bluewhale.video.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 内容评论实体，对应表 {@code vd_comment}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("vd_comment")
public class ContentCommentEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 内容 ID */
    private Long contentId;

    /** 评论用户 ID */
    private Long userId;

    /** 父评论 ID，0 表示顶级 */
    private Long parentId;

    /** 评论正文 */
    private String content;

    /** 评论状态 */
    private String status;

    /** 点赞数 */
    private Integer likeCount;
}
