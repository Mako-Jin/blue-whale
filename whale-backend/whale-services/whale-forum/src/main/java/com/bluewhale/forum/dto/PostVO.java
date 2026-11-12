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

    /** 创建时间 */
    private LocalDateTime createTime;
}
