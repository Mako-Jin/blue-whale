package com.bluewhale.music.domain.model;

import com.bluewhale.music.domain.exception.MusicErrorCode;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 版权下架申诉领域模型（聚合根）。
 *
 * <p>仅版权状态为 {@link CopyrightState#TAKEDOWN} 的作品允许申诉；同一作品同一时刻
 * 只允许存在一条待复核申诉。申诉正文为纯文本且不超过 500 个字符。
 * 状态流转：{@link AppealStatus#PENDING} → {@link AppealStatus#APPROVED} /
 * {@link AppealStatus#REJECTED}，复核后不可再次流转（幂等重放按状态冲突拒绝）。</p>
 *
 * @author BlueWhale
 */
@Data
public class SongAppeal {

    /** 申诉理由最大长度 */
    private static final int REASON_MAX_LENGTH = 500;

    /** 审核意见最大长度 */
    private static final int OPINION_MAX_LENGTH = 500;

    /** 主键 */
    private Long id;

    /** 歌曲 ID */
    private Long songId;

    /** 申诉人用户 ID */
    private Long applicantId;

    /** 申诉理由（纯文本） */
    private String reason;

    /** 申诉状态 */
    private AppealStatus status;

    /** 审核意见（可为空） */
    private String reviewOpinion;

    /** 复核人用户 ID */
    private Long reviewerId;

    /** 复核时间 */
    private LocalDateTime reviewedAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 创建申诉：校验理由长度，初始状态为待复核。
     *
     * @param songId 歌曲 ID
     * @param applicantId 申诉人用户 ID
     * @param reason 申诉理由
     * @return 待复核的申诉
     */
    public static SongAppeal create(Long songId, Long applicantId, String reason) {
        SongAppeal appeal = new SongAppeal();
        appeal.setSongId(songId);
        appeal.setApplicantId(applicantId);
        appeal.setReason(normalizeReason(reason));
        appeal.setStatus(AppealStatus.PENDING);
        return appeal;
    }

    /**
     * 申诉通过：待复核状态流转为 {@link AppealStatus#APPROVED}。
     *
     * @param reviewerId 复核人用户 ID
     * @param opinion 审核意见，可为空
     */
    public void approve(Long reviewerId, String opinion) {
        review(AppealStatus.APPROVED, reviewerId, opinion);
    }

    /**
     * 申诉驳回：待复核状态流转为 {@link AppealStatus#REJECTED}。
     *
     * @param reviewerId 复核人用户 ID
     * @param opinion 审核意见，可为空
     */
    public void reject(Long reviewerId, String opinion) {
        review(AppealStatus.REJECTED, reviewerId, opinion);
    }

    /**
     * 判断申诉是否仍在待复核状态。
     *
     * @return 待复核返回 {@code true}
     */
    public boolean pending() {
        return status == AppealStatus.PENDING;
    }

    /**
     * 复核流转：仅待复核状态可流转，记录复核人与意见。
     *
     * @param target 目标状态
     * @param reviewerUserId 复核人用户 ID
     * @param opinion 审核意见
     */
    private void review(AppealStatus target, Long reviewerUserId, String opinion) {
        if (!pending()) {
            throw MusicErrorCode.exception(MusicErrorCode.APPEAL_STATE_CONFLICT);
        }
        String normalizedOpinion = normalizeOpinion(opinion);
        status = target;
        reviewerId = reviewerUserId;
        reviewedAt = LocalDateTime.now();
        reviewOpinion = normalizedOpinion;
    }

    /**
     * 归一化申诉理由：去首尾空白后非空且不超过 500 个字符。
     *
     * @param reason 申诉理由
     * @return 归一化后的理由
     */
    private static String normalizeReason(String reason) {
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.isEmpty() || trimmed.length() > REASON_MAX_LENGTH) {
            throw MusicErrorCode.exception(MusicErrorCode.APPEAL_REASON_INVALID);
        }
        return trimmed;
    }

    /**
     * 归一化审核意见：去首尾空白，超长按非法参数拒绝。
     *
     * @param opinion 审核意见
     * @return 归一化后的意见；空值返回 {@code null}
     */
    private static String normalizeOpinion(String opinion) {
        String trimmed = opinion == null ? "" : opinion.trim();
        if (trimmed.length() > OPINION_MAX_LENGTH) {
            throw MusicErrorCode.exception(MusicErrorCode.APPEAL_REASON_INVALID);
        }
        return trimmed.isEmpty() ? null : trimmed;
    }
}
