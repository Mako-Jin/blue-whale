package com.bluewhale.party.activity.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 组织生活签到记录。
 *
 * <p>{@link #memberName} 存姓名快照：党员改名后，历史签到表仍应显示当次会议记录上的姓名。
 * 唯一键落在 {@code (activity_id, member_id)}，同一人同一场只有一条记录，
 * 重复签到走更新而不是插第二条（否则实到人数会重复计数）。</p>
 *
 * @author BlueWhale
 */
@Data
public class ActivitySignin {

    /** 缺席原因最大长度 */
    public static final int ABSENT_REASON_MAX_LENGTH = 255;

    /** 主键 */
    private Long id;

    /** 组织生活 ID */
    private Long activityId;

    /** 党员档案 ID */
    private Long memberId;

    /** 党员姓名快照 */
    private String memberName;

    /** 是否到会 */
    private Boolean present;

    /** 缺席原因 */
    private String absentReason;

    /** 签到时间 */
    private LocalDateTime signedAt;

    /**
     * 是否到会（空值按缺席处理：没有明确签到就不算到会）。
     *
     * @return 到会返回 true
     */
    public boolean isPresent() {
        return Boolean.TRUE.equals(present);
    }

    /**
     * 标记到会。
     */
    public void markPresent() {
        this.present = Boolean.TRUE;
        this.absentReason = null;
        this.signedAt = LocalDateTime.now();
    }

    /**
     * 标记缺席。
     *
     * @param reason 缺席原因
     */
    public void markAbsent(String reason) {
        this.present = Boolean.FALSE;
        if (reason != null && !reason.isBlank()) {
            String trimmed = reason.trim();
            this.absentReason = trimmed.length() > ABSENT_REASON_MAX_LENGTH
                    ? trimmed.substring(0, ABSENT_REASON_MAX_LENGTH) : trimmed;
        }
        this.signedAt = LocalDateTime.now();
    }
}
