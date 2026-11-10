package com.bluewhale.music.domain.model;

import com.bluewhale.music.domain.exception.MusicErrorCode;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 歌曲评论领域模型（聚合根）。
 *
 * <p>正文为纯文本且不超过 500 个字符；回复的目标评论必须属于同一歌曲且当前可见。
 * 状态流转：先发后审模式新评论直接 {@link CommentStatus#VISIBLE}，
 * 先审后发模式新评论为 {@link CommentStatus#PENDING}；审核通过变为
 * {@link CommentStatus#VISIBLE}，隐藏后变为 {@link CommentStatus#HIDDEN} 并立即对外不可见。</p>
 *
 * @author BlueWhale
 */
@Data
public class SongComment {

    /** 顶级评论的父评论 ID */
    public static final long TOP_LEVEL_PARENT_ID = 0L;

    /** 评论正文最大长度 */
    private static final int CONTENT_MAX_LENGTH = 500;

    /** 初始点赞数 */
    private static final long DEFAULT_LIKE_COUNT = 0L;

    /** 主键 */
    private Long id;

    /** 歌曲 ID */
    private Long songId;

    /** 评论用户 ID */
    private Long userId;

    /** 父评论 ID，0 表示顶级评论 */
    private Long parentId;

    /** 评论正文（纯文本） */
    private String content;

    /** 状态 */
    private CommentStatus status;

    /** 点赞数 */
    private Long likeCount;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 创建新评论：校验正文长度与回复目标合法性。
     *
     * @param songId 歌曲 ID
     * @param userId 评论用户 ID
     * @param parent 回复的父评论；顶级评论传 {@code null}
     * @param content 评论正文
     * @param preAudit 是否先审后发
     * @return 新评论
     */
    public static SongComment create(Long songId, Long userId, SongComment parent, String content,
                                     boolean preAudit) {
        if (parent != null && (!parent.getSongId().equals(songId) || !parent.visible())) {
            throw MusicErrorCode.exception(MusicErrorCode.COMMENT_PARENT_INVALID);
        }
        SongComment comment = new SongComment();
        comment.setSongId(songId);
        comment.setUserId(userId);
        comment.setParentId(parent == null ? TOP_LEVEL_PARENT_ID : parent.getId());
        comment.fillContent(content);
        comment.setStatus(preAudit ? CommentStatus.PENDING : CommentStatus.VISIBLE);
        comment.setLikeCount(DEFAULT_LIKE_COUNT);
        return comment;
    }

    /**
     * 填充正文：去首尾空白后非空且不超过 500 个字符。
     *
     * @param content 评论正文
     */
    public void fillContent(String content) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isEmpty() || trimmed.length() > CONTENT_MAX_LENGTH) {
            throw MusicErrorCode.exception(MusicErrorCode.COMMENT_CONTENT_INVALID);
        }
        this.content = trimmed;
    }

    /**
     * 评论当前是否对外可见。
     *
     * @return 可见返回 {@code true}
     */
    public boolean visible() {
        return status == CommentStatus.VISIBLE;
    }

    /**
     * 审核通过：非可见状态变为可见。
     *
     * @return 本次流转需要增加歌曲评论计数返回 {@code true}；已是可见状态幂等返回 {@code false}
     */
    public boolean approve() {
        if (status == CommentStatus.VISIBLE) {
            return false;
        }
        status = CommentStatus.VISIBLE;
        return true;
    }

    /**
     * 隐藏评论：隐藏后立即对外不可见。
     *
     * @return 本次流转需要减少歌曲评论计数返回 {@code true}；已是隐藏状态幂等返回 {@code false}
     */
    public boolean hide() {
        if (status == CommentStatus.HIDDEN) {
            return false;
        }
        boolean wasVisible = status == CommentStatus.VISIBLE;
        status = CommentStatus.HIDDEN;
        return wasVisible;
    }
}
