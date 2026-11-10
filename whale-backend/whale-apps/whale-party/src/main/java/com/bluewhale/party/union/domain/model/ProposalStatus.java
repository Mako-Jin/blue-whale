package com.bluewhale.party.union.domain.model;

import com.bluewhale.party.common.PartyErrorCode;

import java.util.Locale;

/**
 * 职代会提案状态。
 *
 * <p>流转严格单向：{@code SUBMITTED → FILED → HANDLING → FEEDBACK → RATED}。
 * 不允许跳步或回退，否则办理节点字段会缺失，提案追溯时无法解释「谁、何时、做了什么」。</p>
 *
 * @author BlueWhale
 */
public enum ProposalStatus {

    /** 已提交 */
    SUBMITTED("已提交"),

    /** 已立案 */
    FILED("已立案"),

    /** 办理中 */
    HANDLING("办理中"),

    /** 已反馈 */
    FEEDBACK("已反馈"),

    /** 已评价（终态） */
    RATED("已评价");

    /** 中文标签 */
    private final String label;

    /**
     * 构造状态。
     *
     * @param label 中文标签
     */
    ProposalStatus(String label) {
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
     * 解析状态；空值回落已提交（新提案的默认口径）。
     *
     * @param value 状态文本
     * @return 状态枚举
     */
    public static ProposalStatus parse(String value) {
        if (value == null || value.isBlank()) {
            return SUBMITTED;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_PROPOSAL_STATE_CONFLICT,
                    "提案状态不合法：" + value);
        }
    }

    /**
     * 是否终态。
     *
     * @return 已评价返回 true
     */
    public boolean isTerminal() {
        return this == RATED;
    }
}
