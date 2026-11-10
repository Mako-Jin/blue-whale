package com.bluewhale.party.content.domain.model;

/**
 * 党建内容可见范围。
 *
 * <p>三种范围是互斥的，不叠加：选「指定支部」就只看这些支部，不再顺带对全体党员开放。
 * 叠加语义会让「只发给三个支部的征求意见稿」变成全体党员都能看，一次误发无法回收。</p>
 *
 * @author BlueWhale
 */
public enum ContentVisibility {

    /** 全体党员（需有在册党员档案） */
    ALL_MEMBERS("全体党员"),

    /** 指定支部（仅所选支部的在册党员） */
    BRANCHES("指定支部"),

    /** 全员（本单位所有登录用户） */
    ALL_STAFF("全员");

    /** 中文标签 */
    private final String label;

    /**
     * 构造可见范围。
     *
     * @param label 中文标签
     */
    ContentVisibility(String label) {
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
     * 解析可见范围；未知值与空值回落为全体党员（默认最保守的党务口径）。
     *
     * @param code 范围码
     * @return 可见范围
     */
    public static ContentVisibility parse(String code) {
        if (code == null || code.isBlank()) {
            return ALL_MEMBERS;
        }
        for (ContentVisibility visibility : values()) {
            if (visibility.name().equalsIgnoreCase(code.trim())) {
                return visibility;
            }
        }
        return ALL_MEMBERS;
    }
}
