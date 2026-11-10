package com.bluewhale.party.content.domain.model;

/**
 * 党建内容状态（先审后发）。
 *
 * <p>流转是<b>单向</b>的：草稿 → 待审 → 已发布 → 已下线；待审可被退回草稿，
 * 已发布内容一旦修改即回到待审。原因：党建内容对外代表组织口径，
 * 允许「编辑即生效」等于绕开审核，修改后没人复核就发布出去了。</p>
 *
 * @author BlueWhale
 */
public enum ContentStatus {

    /** 草稿（可编辑） */
    DRAFT("草稿"),

    /** 待审（只读，等待审核人处理） */
    PENDING("待审"),

    /** 已发布（读者可见，修改后退回待审） */
    PUBLISHED("已发布"),

    /** 已下线（终态，保留历史与阅读数） */
    OFFLINE("已下线");

    /** 中文标签 */
    private final String label;

    /**
     * 构造状态。
     *
     * @param label 中文标签
     */
    ContentStatus(String label) {
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
     * 是否为终态（已下线不可再流转）。
     *
     * @return 终态返回 true
     */
    public boolean isTerminal() {
        return this == OFFLINE;
    }

    /**
     * 是否为已发布。
     *
     * @return 已发布返回 true
     */
    public boolean isPublished() {
        return this == PUBLISHED;
    }

    /**
     * 解析状态；未知值与空值回落为草稿。
     *
     * @param code 状态码
     * @return 状态
     */
    public static ContentStatus parse(String code) {
        if (code == null || code.isBlank()) {
            return DRAFT;
        }
        for (ContentStatus status : values()) {
            if (status.name().equalsIgnoreCase(code.trim())) {
                return status;
            }
        }
        return DRAFT;
    }
}
