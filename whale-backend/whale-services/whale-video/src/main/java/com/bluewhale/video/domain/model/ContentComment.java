package com.bluewhale.video.domain.model;

import com.bluewhale.video.domain.exception.VideoErrorCode;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 内容评论（支持一级回复，正文为纯文本且不超过 500 字）。
 *
 * @author BlueWhale
 */
@Data
public class ContentComment {

    /** 正文最大长度 */
    public static final int CONTENT_MAX_LENGTH = 500;

    /** 主键 */
    private Long id;

    /** 内容 ID */
    private Long contentId;

    /** 评论用户 ID */
    private Long userId;

    /** 父评论 ID，0 表示顶级评论 */
    private Long parentId;

    /** 评论正文 */
    private String content;

    /** 评论状态 */
    private CommentStatus status;

    /** 点赞数 */
    private Integer likeCount;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 校验并规范化评论正文（去除首尾空白）。
     *
     * @param raw 原始正文
     * @return 规范化正文
     */
    public static String normalize(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty() || text.length() > CONTENT_MAX_LENGTH) {
            throw VideoErrorCode.exception(VideoErrorCode.COMMENT_CONTENT_INVALID);
        }
        return text;
    }

    /**
     * 判断评论当前对用户侧是否可见。
     *
     * @return 仅 {@link CommentStatus#VISIBLE} 返回 {@code true}
     */
    public boolean visible() {
        return status == CommentStatus.VISIBLE;
    }
}
