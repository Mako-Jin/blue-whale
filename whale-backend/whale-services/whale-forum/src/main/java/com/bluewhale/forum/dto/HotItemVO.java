package com.bluewhale.forum.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 热榜条目（只读快照视图）。
 *
 * <p>字段取快照时点的值，<b>不随帖子实时变化</b>：热榜的意义是「这一轮大家在看什么」，
 * 若每次打开都回查实时计数，同一榜单在不同时刻会长得不一样，反而无法解释排名。</p>
 *
 * @author BlueWhale
 */
@Data
public class HotItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 名次（1 起） */
    private Integer rankNo;

    /** 帖子ID */
    private Long threadId;

    /** 版块ID */
    private Long boardId;

    /** 标题 */
    private String title;

    /** 摘要 */
    private String summary;

    /** 作者用户ID */
    private Long authorId;

    /** 回复数（快照时点） */
    private Integer replyCount;

    /** 投花数（快照时点） */
    private Integer likeCount;

    /** 浏览数（快照时点） */
    private Integer viewCount;

    /** 热度分 */
    private BigDecimal score;

    /** 快照时间 */
    private LocalDateTime snapshotTime;
}
