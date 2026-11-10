package com.bluewhale.party.activity.domain.model;

/**
 * 组织生活状态。
 *
 * <p>三态单向流转：计划 → 完成 → 归档。归档是<b>终态</b>，语义是「本次组织生活材料定稿」，
 * 归档后不可再改计划、不可再签到——否则「这次会议到会几人」会在事后被悄悄改写，
 * 年度考核与上级检查就对不上账。</p>
 *
 * @author BlueWhale
 */
public enum ActivityStatus {

    /** 计划中 */
    PLANNED("计划中"),

    /** 已召开 */
    COMPLETED("已完成"),

    /** 已归档（终态） */
    ARCHIVED("已归档");

    /** 中文标签 */
    private final String label;

    /**
     * 构造状态。
     *
     * @param label 中文标签
     */
    ActivityStatus(String label) {
        this.label = label;
    }

    /**
     * 查询中文标签。
     *
     * @return 中文标签
     */
    public String getLabel() {
        return label;
    }

    /**
     * 是否终态（归档后不可再变更）。
     *
     * @return 终态返回 true
     */
    public boolean isTerminal() {
        return this == ARCHIVED;
    }

    /**
     * 解析状态；未知值与空值回落为计划中（宁可让人看到一条待办，也不要静默当成已完成）。
     *
     * @param code 状态码
     * @return 状态
     */
    public static ActivityStatus parse(String code) {
        if (code == null || code.isBlank()) {
            return PLANNED;
        }
        for (ActivityStatus status : values()) {
            if (status.name().equalsIgnoreCase(code.trim())) {
                return status;
            }
        }
        return PLANNED;
    }
}
