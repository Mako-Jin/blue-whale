package com.bluewhale.forum.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 楼层视图。
 *
 * @author BlueWhale
 */
@Data
public class PostVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 楼层ID */
    private Long id;

    /** 楼层号（1..N） */
    private Integer floor;

    /** 作者用户ID */
    private Long authorId;

    /** 是否匿名展示：0 否 / 1 是 */
    private Integer anonymous;

    /** 匿名编号 */
    private String anonymousNo;

    /** 正文（已消毒） */
    private String content;

    /** 投花数 */
    private Integer likeCount;

    /** 引用楼层ID（被引用楼层已删时前端占位） */
    private Long replyToPostId;

    /** 被引用楼层摘要（未引用时为 {@code null}；已删除时 {@code deleted=true}） */
    private QuotedPostVO quoted;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 楼层状态：PUBLISHED 已发布 / PENDING 待审（待审楼层不出现在楼层列表） */
    private String status;
}
