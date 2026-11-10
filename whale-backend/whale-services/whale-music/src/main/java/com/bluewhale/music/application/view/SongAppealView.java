package com.bluewhale.music.application.view;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 版权下架申诉视图（用户侧与复核侧共用；复核侧额外填充歌曲标题）。
 *
 * @author BlueWhale
 */
@Data
public class SongAppealView {

    /** 申诉 ID */
    private Long id;

    /** 歌曲 ID */
    private Long songId;

    /** 歌曲标题（复核侧展示用） */
    private String songTitle;

    /** 申诉人用户 ID */
    private Long applicantId;

    /** 申诉理由 */
    private String reason;

    /** 状态：PENDING / APPROVED / REJECTED */
    private String status;

    /** 审核意见 */
    private String reviewOpinion;

    /** 复核人用户 ID */
    private Long reviewerId;

    /** 复核时间 */
    private LocalDateTime reviewedAt;

    /** 提交时间 */
    private LocalDateTime createTime;
}
