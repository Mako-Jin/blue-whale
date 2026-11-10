package com.bluewhale.lms.exam.domain.model;

import com.bluewhale.lms.common.LmsErrorCode;

import java.util.Locale;

/**
 * 试卷状态：{@code DRAFT → PUBLISHED → ARCHIVED}。
 *
 * <p>与课程 / 培训班同口径：仅草稿可改题目与头信息；发布后题目冻结，
 * 避免已开考的试卷被改题；归档为终态，只可查。</p>
 *
 * @author BlueWhale
 */
public enum PaperStatus {

    /** 草稿：可增删题目、可规则抽题 */
    DRAFT,

    /** 已发布：题目冻结，可被考试引用 */
    PUBLISHED,

    /** 已归档：终态只读 */
    ARCHIVED;

    /**
     * 是否可编辑内容。
     *
     * @return 仅草稿返回 true
     */
    public boolean canEditContent() {
        return this == DRAFT;
    }

    /**
     * 是否可发布。
     *
     * @return 仅草稿返回 true
     */
    public boolean canPublish() {
        return this == DRAFT;
    }

    /**
     * 是否可被考试引用。
     *
     * @return 仅已发布返回 true
     */
    public boolean canUseForExam() {
        return this == PUBLISHED;
    }

    /**
     * 解析状态。
     *
     * @param raw 原始值
     * @return 状态
     */
    public static PaperStatus parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw LmsErrorCode.exception(LmsErrorCode.PAPER_STATE_CONFLICT, "试卷状态不能为空");
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        for (PaperStatus status : values()) {
            if (status.name().equals(normalized)) {
                return status;
            }
        }
        throw LmsErrorCode.exception(LmsErrorCode.PAPER_STATE_CONFLICT, "不支持的试卷状态：" + raw);
    }
}
