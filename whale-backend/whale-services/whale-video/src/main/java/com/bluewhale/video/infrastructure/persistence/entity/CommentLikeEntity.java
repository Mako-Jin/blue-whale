package com.bluewhale.video.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 评论点赞实体，对应表 {@code vd_comment_like}。
 *
 * @author BlueWhale
 */
@Data
@TableName("vd_comment_like")
public class CommentLikeEntity {

    /** 主键（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 评论 ID */
    private Long commentId;

    /** 用户 ID */
    private Long userId;
}
