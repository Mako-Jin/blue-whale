package com.bluewhale.video.application.view;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 评论视图（含一级回复与当前用户点赞状态）。
 *
 * @author BlueWhale
 */
@Data
public class CommentView implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 评论 ID */
    private Long id;

    /** 内容 ID */
    private Long contentId;

    /** 评论用户 ID */
    private Long userId;

    /** 父评论 ID，0 表示顶级 */
    private Long parentId;

    /** 评论正文 */
    private String content;

    /** 点赞数 */
    private Integer likeCount;

    /** 当前用户是否已点赞 */
    private boolean liked;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 一级回复 */
    private List<CommentView> replies;
}
