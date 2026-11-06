package com.bluewhale.party.content.domain.model;

/**
 * 党建内容审核动作。
 *
 * <p>{@link #EDIT} 是「已发布内容被修改」触发的隐式流转：不需要审核人操作，
 * 但必须留痕，否则线上内容突然消失（变回待审）时查不出是谁改的。</p>
 *
 * @author BlueWhale
 */
public enum AuditAction {

    /** 提交审核 */
    SUBMIT("提交审核"),

    /** 审核通过 */
    APPROVE("审核通过"),

    /** 审核退回 */
    REJECT("审核退回"),

    /** 下线 */
    OFFLINE("下线"),

    /** 发布态修改（自动退回待审） */
    EDIT("发布态修改");

    /** 中文标签 */
    private final String label;

    /**
     * 构造动作。
     *
     * @param label 中文标签
     */
    AuditAction(String label) {
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
     * 解析动作；未知值与空值回落为提交审核。
     *
     * @param code 动作码
     * @return 动作
     */
    public static AuditAction parse(String code) {
        if (code == null || code.isBlank()) {
            return SUBMIT;
        }
        for (AuditAction action : values()) {
            if (action.name().equalsIgnoreCase(code.trim())) {
                return action;
            }
        }
        return SUBMIT;
    }
}
