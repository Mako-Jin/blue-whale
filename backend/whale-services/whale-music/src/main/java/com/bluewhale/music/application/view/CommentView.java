package com.bluewhale.music.application.view;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 歌曲评论视图（用户侧与审核侧共用；审核侧额外填充歌曲标题）。
 *
 * @author BlueWhale
 */
@Data
public class CommentView {

    /** 评论 ID */
    private Long id;

    /** 歌曲 ID */
    private Long songId;

    /** 歌曲标题（审核侧展示用，用户侧为空） */
    private String songTitle;

    /** 评论用户 ID */
    private Long userId;

    /** 父评论 ID，0 表示顶级评论 */
    private Long parentId;

    /** 评论正文 */
    private String content;

    /** 状态：PENDING / VISIBLE / HIDDEN（用户侧列表恒为 VISIBLE） */
    private String status;

    /** 点赞数 */
    private Long likeCount;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 可见回复列表（仅用户侧顶级评论填充） */
    private List<CommentView> replies;
}
