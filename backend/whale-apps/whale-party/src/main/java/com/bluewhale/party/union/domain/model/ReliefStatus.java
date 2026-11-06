package com.bluewhale.party.union.domain.model;

import com.bluewhale.party.common.PartyErrorCode;

import java.util.Locale;

/**
 * 慰问帮扶状态。
 *
 * <p>状态单向：{@code DRAFT → SUBMITTED → APPROVED / REJECTED}，{@code APPROVED → PAID}。
 * {@code REJECTED} 与 {@code PAID} 为终态；退回后要再申请只能另建单，不复活旧单，
 * 否则同一申请号会对应两次审批，审计上无法还原。</p>
 *
 * @author BlueWhale
 */
public enum ReliefStatus {

    /** 草稿（含审批不可用降级） */
    DRAFT("草稿"),

    /** 审批中 */
    SUBMITTED("审批中"),

    /** 审批通过 */
    APPROVED("已通过"),

    /** 审批退回（终态） */
    REJECTED("已退回"),

    /** 已发放（终态） */
    PAID("已发放");

    /** 中文标签 */
    private final String label;

    /**
     * 构造状态。
     *
     * @param label 中文标签
     */
    ReliefStatus(String label) {
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
    public static ReliefStatus parse(String value) {
        if (value == null || value.isBlank()) {
            return DRAFT;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_RELIEF_STATE_CONFLICT,
                    "慰问帮扶状态不合法：" + value);
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
     * 是否审批中。
     *
     * @return 审批中返回 true
     */
    public boolean isSubmitted() {
        return this == SUBMITTED;
    }

    /**
     * 是否已批（已通过或已发放）。
     *
     * <p>上限统计必须同时算上已通过与已发放：只统计已通过会让「批了但还没发」的额度
     * 在下一笔申请里被重复使用。</p>
     *
     * @return 已批返回 true
     */
    public boolean isApprovedLike() {
        return this == APPROVED || this == PAID;
    }

    /**
     * 是否终态。
     *
     * @return 退回或已发放返回 true
     */
    public boolean isTerminal() {
        return this == REJECTED || this == PAID;
    }
}
