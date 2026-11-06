package com.bluewhale.lms.exam.domain.model;

import com.bluewhale.lms.common.LmsErrorCode;

import java.util.Locale;

/**
 * 题型。
 *
 * <p>判定「是否客观题」是判分口径的唯一来源：客观题交卷即自动判分，
 * 主观题（简答）进入人工判分队列。</p>
 *
 * @author BlueWhale
 */
public enum QuestionType {

    /** 单选题 */
    SINGLE,

    /** 多选题（全对得满分，不做半分） */
    MULTIPLE,

    /** 判断题 */
    JUDGE,

    /** 简答题（人工判分） */
    SHORT_ANSWER;

    /**
     * 是否客观题。
     *
     * @return 简答题以外均为客观题
     */
    public boolean isObjective() {
        return this != SHORT_ANSWER;
    }

    /**
     * 是否需要选项。
     *
     * @return 单选 / 多选返回 true
     */
    public boolean requiresOptions() {
        return this == SINGLE || this == MULTIPLE;
    }

    /**
     * 是否多选（答案按集合比较）。
     *
     * @return 多选返回 true
     */
    public boolean isMultiple() {
        return this == MULTIPLE;
    }

    /**
     * 解析题型（大小写与空白不敏感）。
     *
     * @param raw 原始值
     * @return 题型
     */
    public static QuestionType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw LmsErrorCode.exception(LmsErrorCode.QUESTION_INVALID, "题型不能为空");
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        for (QuestionType type : values()) {
            if (type.name().equals(normalized)) {
                return type;
            }
        }
        throw LmsErrorCode.exception(LmsErrorCode.QUESTION_INVALID, "不支持的题型：" + raw);
    }
}
