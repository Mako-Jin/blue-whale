package com.bluewhale.party.activity.domain.model;

/**
 * 组织生活类型（三会一课与主题党日）。
 *
 * <p>类型只决定展示与统计口径，不改变流程：五类都是「计划 → 完成 → 归档」，
 * 若为每类各写一套状态机，后续加一种类型就要复制一遍流程。</p>
 *
 * @author BlueWhale
 */
public enum ActivityType {

    /** 支委会 */
    BRANCH_COMMITTEE("支委会"),

    /** 党员大会 */
    MEMBER_ASSEMBLY("党员大会"),

    /** 党小组会 */
    PARTY_GROUP("党小组会"),

    /** 党课 */
    PARTY_LECTURE("党课"),

    /** 主题党日 */
    THEME_DAY("主题党日");

    /** 中文标签 */
    private final String label;

    /**
     * 构造类型。
     *
     * @param label 中文标签
     */
    ActivityType(String label) {
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
     * 解析类型；未知值与空值回落为党员大会（默认最常用的一类）。
     *
     * @param code 类型码
     * @return 类型
     */
    public static ActivityType parse(String code) {
        if (code == null || code.isBlank()) {
            return MEMBER_ASSEMBLY;
        }
        for (ActivityType type : values()) {
            if (type.name().equalsIgnoreCase(code.trim())) {
                return type;
            }
        }
        return MEMBER_ASSEMBLY;
    }
}
