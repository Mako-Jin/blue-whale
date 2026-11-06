package com.bluewhale.lms.exam.domain.model;

import com.bluewhale.lms.common.LmsErrorCode;

import java.util.Locale;

/**
 * 作答状态：{@code IN_PROGRESS → SUBMITTED → GRADED}，超时未交为 {@code EXPIRED}。
 *
 * <p>{@code SUBMITTED} 表示「已交卷但有主观题待判」，{@code GRADED} 表示全部题目
 * 判完并已生成成绩版本。</p>
 *
 * @author BlueWhale
 */
public enum AttemptStatus {

    /** 作答中：可保存答案、可交卷 */
    IN_PROGRESS,

    /** 已交卷：客观题已判，主观题待人工判分 */
    SUBMITTED,

    /** 已判分：全部题目判完，成绩已生成 */
    GRADED,

    /** 超时未交卷（保留已保存答案，不再接受提交） */
    EXPIRED;

    /**
     * 是否可继续作答（保存 / 提交）。
     *
     * @return 仅作答中返回 true
     */
    public boolean isOpen() {
        return this == IN_PROGRESS;
    }

    /**
     * 解析状态。
     *
     * @param raw 原始值
     * @return 状态
     */
    public static AttemptStatus parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw LmsErrorCode.exception(LmsErrorCode.ATTEMPT_STATE_CONFLICT, "作答状态不能为空");
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        for (AttemptStatus status : values()) {
            if (status.name().equals(normalized)) {
                return status;
            }
        }
        throw LmsErrorCode.exception(LmsErrorCode.ATTEMPT_STATE_CONFLICT, "不支持的作答状态：" + raw);
    }
}
