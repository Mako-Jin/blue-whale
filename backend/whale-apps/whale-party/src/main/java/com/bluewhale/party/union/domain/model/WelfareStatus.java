package com.bluewhale.party.union.domain.model;

import com.bluewhale.party.common.PartyErrorCode;

import java.util.Locale;

/**
 * 节日福利批次状态。
 *
 * <p>状态单向：{@code DRAFT → ISSUED → CLOSED}。只有草稿可编辑，发放后名单与金额冻结；
 * 关闭是终态，关闭后不允许再签收，避免「先关账再补签」把对账口径打乱。</p>
 *
 * @author BlueWhale
 */
public enum WelfareStatus {

    /** 草稿（可编辑） */
    DRAFT("草稿"),

    /** 已发放（可签收） */
    ISSUED("已发放"),

    /** 已关闭（终态） */
    CLOSED("已关闭");

    /** 中文标签 */
    private final String label;

    /**
     * 构造状态。
     *
     * @param label 中文标签
     */
    WelfareStatus(String label) {
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
    public static WelfareStatus parse(String value) {
        if (value == null || value.isBlank()) {
            return DRAFT;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_WELFARE_STATE_CONFLICT,
                    "节日福利状态不合法：" + value);
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
     * 是否可编辑（仅草稿）。
     *
     * @return 可编辑返回 true
     */
    public boolean isEditable() {
        return this == DRAFT;
    }

    /**
     * 是否已发放。
     *
     * @return 已发放返回 true
     */
    public boolean isIssued() {
        return this == ISSUED;
    }

    /**
     * 是否已关闭。
     *
     * @return 已关闭返回 true
     */
    public boolean isClosed() {
        return this == CLOSED;
    }
}
