package com.bluewhale.music.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 版权下架申诉实体，对应表 {@code mu_song_appeal}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mu_song_appeal")
public class SongAppealEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 歌曲 ID */
    private Long songId;

    /** 申诉人用户 ID */
    private Long applicantId;

    /** 申诉理由（纯文本） */
    private String reason;

    /** 状态：PENDING / APPROVED / REJECTED */
    private String status;

    /** 审核意见（可为空） */
    private String reviewOpinion;

    /** 复核人用户 ID */
    private Long reviewerId;

    /** 复核时间 */
    private LocalDateTime reviewedAt;
}
