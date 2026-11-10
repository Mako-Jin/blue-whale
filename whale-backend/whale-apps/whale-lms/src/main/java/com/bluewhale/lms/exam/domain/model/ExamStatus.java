package com.bluewhale.lms.exam.domain.model;

import com.bluewhale.lms.common.LmsErrorCode;

import java.util.Locale;

/**
 * 考试状态：{@code DRAFT → PUBLISHED → CLOSED}。
 *
 * <p>仅已发布可作答；结课后不再接受新的作答，历史成绩保留。</p>
 *
 * @author BlueWhale
 */
public enum ExamStatus {

    /** 草稿：可改配置，不可作答 */
    DRAFT,

    /** 已发布：窗口内可作答 */
    PUBLISHED,

    /** 已结课：终态，只可查 */
    CLOSED;

    /**
     * 是否可编辑配置。
     *
     * @return 仅草稿返回 true
     */
    public boolean canEdit() {
        return this == DRAFT;
    }

    /**
     * 是否接受作答。
     *
     * @return 仅已发布返回 true
     */
    public boolean canAttempt() {
        return this == PUBLISHED;
    }

    /**
     * 是否可删除。
     *
     * @return 仅草稿返回 true
     */
    public boolean canDelete() {
        return this == DRAFT;
    }

    /**
     * 解析状态。
     *
     * @param raw 原始值
     * @return 状态
     */
    public static ExamStatus parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw LmsErrorCode.exception(LmsErrorCode.EXAM_STATE_CONFLICT, "考试状态不能为空");
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        for (ExamStatus status : values()) {
            if (status.name().equals(normalized)) {
                return status;
            }
        }
        throw LmsErrorCode.exception(LmsErrorCode.EXAM_STATE_CONFLICT, "不支持的考试状态：" + raw);
    }
}
