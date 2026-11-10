package com.bluewhale.lms.course.domain.model;

import com.bluewhale.lms.common.LmsErrorCode;

import java.util.Arrays;

/**
 * 课程发布状态机：DRAFT → PUBLISHED → ARCHIVED。
 *
 * <p>规则（REQ-FUNC-087）：仅 DRAFT 可编辑内容与删除课程；仅 DRAFT 可发布
 * 且发布前至少 1 个章节；PUBLISHED 可归档下架；ARCHIVED 为终态只读。
 * 内容（章节/课件）在 PUBLISHED 态不可变更，需先归档。</p>
 *
 * @author BlueWhale
 */
public enum CourseStatus {

    /** 草稿 */
    DRAFT,

    /** 已发布（可报名学习） */
    PUBLISHED,

    /** 已归档（下架，只读） */
    ARCHIVED;

    /**
     * 是否可发布。
     *
     * @return DRAFT 返回 true
     */
    public boolean canPublish() {
        return this == DRAFT;
    }

    /**
     * 是否可归档。
     *
     * @return DRAFT / PUBLISHED 返回 true
     */
    public boolean canArchive() {
        return this == DRAFT || this == PUBLISHED;
    }

    /**
     * 是否可编辑内容（章节 / 课件 / 头信息）。
     *
     * <p>仅 DRAFT 可编辑；PUBLISHED 内容冻结（需先归档）；ARCHIVED 为终态只读。</p>
     *
     * @return DRAFT 返回 true
     */
    public boolean canEditContent() {
        return this == DRAFT;
    }

    /**
     * 是否可逻辑删除。
     *
     * @return 非 PUBLISHED 返回 true
     */
    public boolean canDelete() {
        return this != PUBLISHED;
    }

    /**
     * 是否已发布（可报名学习）。
     *
     * @return PUBLISHED 返回 true
     */
    public boolean isPublished() {
        return this == PUBLISHED;
    }

    /**
     * 解析状态文本。
     *
     * @param value 状态文本
     * @return 状态枚举
     */
    public static CourseStatus of(String value) {
        return Arrays.stream(values())
                .filter(status -> status.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> LmsErrorCode.exception(LmsErrorCode.COURSE_STATE_CONFLICT,
                        "课程状态非法：" + value));
    }
}
