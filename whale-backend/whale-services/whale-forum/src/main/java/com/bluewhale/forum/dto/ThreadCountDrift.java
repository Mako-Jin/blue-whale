package com.bluewhale.forum.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 单帖计数漂移（展示值 vs 明细 COUNT）。
 *
 * @author BlueWhale
 */
@Data
public class ThreadCountDrift implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 帖子ID */
    private Long threadId;

    /** 展示用回复数 */
    private Integer replyCount;

    /** 展示用投花数 */
    private Integer likeCount;

    /** 展示用收藏数 */
    private Integer collectCount;

    /** 明细重算的回复数 */
    private Integer actualReply;

    /** 明细重算的投花数 */
    private Integer actualLike;

    /** 明细重算的收藏数 */
    private Integer actualCollect;
}
