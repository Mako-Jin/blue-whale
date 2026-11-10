package com.bluewhale.lms.exam.domain.model;

import com.bluewhale.lms.common.LmsErrorCode;

import java.util.Locale;

/**
 * 题目状态：{@code DRAFT → ACTIVE}。
 *
 * <p>草稿允许不完整（便于分步录入），启用前必须通过完整性校验；
 * 启用后的题目可被抽题规则命中，编辑不影响已发布试卷（试卷持有题目快照分值）。</p>
 *
 * @author BlueWhale
 */
public enum QuestionStatus {

    /** 草稿：可不完整，不参与抽题 */
    DRAFT,

    /** 启用：完整性校验通过，可被抽题 */
    ACTIVE,

    /** 停用：保留历史，不再参与抽题 */
    DISABLED;

    /**
     * 是否可编辑。
     *
     * @return 非停用状态均返回 true
     */
    public boolean canEdit() {
        return this != DISABLED;
    }

    /**
     * 是否可参与抽题。
     *
     * @return 仅启用返回 true
     */
    public boolean canPick() {
        return this == ACTIVE;
    }

    /**
     * 解析状态（空值按草稿处理）。
     *
     * @param raw 原始值
     * @return 状态
     */
    public static QuestionStatus parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return DRAFT;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        for (QuestionStatus status : values()) {
            if (status.name().equals(normalized)) {
                return status;
            }
        }
        throw LmsErrorCode.exception(LmsErrorCode.QUESTION_STATE_CONFLICT, "不支持的题目状态：" + raw);
    }
}
