package com.bluewhale.lms.training.domain.model;

/**
 * 培训班状态机：{@code DRAFT → PUBLISHED → CLOSED}。
 *
 * <p>口径与课程一致：仅草稿可编辑内容（场次 / 头信息）；发布后内容冻结，避免
 * 已分配学员看到的场次时间被改动；{@code CLOSED} 为终态，只可查与统计，
 * 不再接受分配与签到。</p>
 *
 * @author BlueWhale
 */
public enum TrainingClassStatus {

    /** 草稿：可编辑场次与头信息，可删除 */
    DRAFT,

    /** 已发布：内容冻结，可分配学员、可签到，不可删除 */
    PUBLISHED,

    /** 已结课：终态，只读 */
    CLOSED;

    /**
     * 是否可编辑内容（头信息 / 场次）。
     *
     * @return 仅草稿返回 true
     */
    public boolean canEditContent() {
        return this == DRAFT;
    }

    /**
     * 是否可分配学员。
     *
     * @return 仅已发布返回 true
     */
    public boolean canAssign() {
        return this == PUBLISHED;
    }

    /**
     * 是否可签到。
     *
     * @return 仅已发布返回 true
     */
    public boolean canCheckin() {
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
     * 是否可发布。
     *
     * @return 仅草稿返回 true
     */
    public boolean canPublish() {
        return this == DRAFT;
    }

    /**
     * 是否可结课。
     *
     * @return 仅已发布返回 true
     */
    public boolean canClose() {
        return this == PUBLISHED;
    }
}
