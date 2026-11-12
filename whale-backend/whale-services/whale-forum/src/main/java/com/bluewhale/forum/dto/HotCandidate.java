package com.bluewhale.forum.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 热榜候选投影（{@code ForumThreadMapper.selectHotCandidates} 的出参）。
 *
 * <p>不是对外视图：只承载打分所需的列与算好的 {@code score}，
 * 对外一律走 {@link HotItemVO}，避免把内部打分字段固化进契约。</p>
 *
 * @author BlueWhale
 */
@Data
public class HotCandidate implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 帖子ID */
    private Long id;

    /** 版块ID */
    private Long boardId;

    /** 标题 */
    private String title;

    /** 摘要 */
    private String summary;

    /** 作者 */
    private Long authorId;

    /** 是否匿名（0/1）：匿名帖不参与金榜经验，打分不受影响 */
    private Integer anonymous;

    /** 回复数 */
    private Integer replyCount;

    /** 投花数 */
    private Integer likeCount;

    /** 浏览数 */
    private Integer viewCount;

    /** 热度分 */
    private BigDecimal score;
}
