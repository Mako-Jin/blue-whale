package com.bluewhale.music.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 歌曲评论实体，对应表 {@code mu_song_comment}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mu_song_comment")
public class SongCommentEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 歌曲 ID */
    private Long songId;

    /** 评论用户 ID */
    private Long userId;

    /** 父评论 ID，0 表示顶级评论 */
    private Long parentId;

    /** 评论正文（纯文本） */
    private String content;

    /** 状态：PENDING / VISIBLE / HIDDEN */
    private String status;

    /** 点赞数 */
    private Long likeCount;
}
