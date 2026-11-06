package com.bluewhale.party.union.domain.model;

import com.bluewhale.party.common.PartyErrorCode;

import java.util.Locale;

/**
 * 工会活动状态。
 *
 * <p>流转：{@code DRAFT → PUBLISHED → CLOSED}；{@code DRAFT / PUBLISHED → CANCELLED}。
 * {@code CLOSED} 与 {@code CANCELLED} 是终态，不可再发布或关闭，
 * 否则已报名名单会对应到一个「又活了」的活动。</p>
 *
 * @author BlueWhale
 */
public enum UnionActivityStatus {

    /** 草稿（可编辑） */
    DRAFT("草稿"),

    /** 已发布（可报名） */
    PUBLISHED("已发布"),

    /** 已结束（终态） */
    CLOSED("已结束"),

    /** 已取消（终态） */
    CANCELLED("已取消");

    /** 中文标签 */
    private final String label;

    /**
     * 构造状态。
     *
     * @param label 中文标签
     */
    UnionActivityStatus(String label) {
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
     * 解析状态；空值回落草稿（保存前未显式赋值的默认口径）。
     *
     * @param value 状态文本
     * @return 状态枚举
     */
    public static UnionActivityStatus parse(String value) {
        if (value == null || value.isBlank()) {
            return DRAFT;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_ACTIVITY_STATE_CONFLICT,
                    "工会活动状态不合法：" + value);
        }
    }

    /**
     * 是否草稿。
     *
     * @return 草稿返回 true
     */
    public boolean isDraft() {
        return this == DRAFT;
    }

    /**
     * 是否已发布。
     *
     * @return 已发布返回 true
     */
    public boolean isPublished() {
        return this == PUBLISHED;
    }

    /**
     * 是否终态（已结束或已取消）。
     *
     * @return 终态返回 true
     */
    public boolean isTerminal() {
        return this == CLOSED || this == CANCELLED;
    }
}
