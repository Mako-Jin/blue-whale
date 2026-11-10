package com.bluewhale.contract.domain;

/**
 * 合同状态机状态常量。
 *
 * <pre>
 * DRAFT ──提交──► APPROVING ──通过──► APPROVED ──发起签署──► SIGNING ──全部签署──► SIGNED ──生效──► EFFECTIVE ──归档──► ARCHIVED
 *   ▲               │
 *   │               └──驳回──► REJECTED ──修订重提──► DRAFT（version+1）
 *   └──────────────────────撤回 WITHDRAWN（仅 APPROVING）
 * </pre>
 *
 * @author BlueWhale
 */
public enum ContractStatus {

    /** 草稿 */
    DRAFT,

    /** 审批中 */
    APPROVING,

    /** 审批通过 */
    APPROVED,

    /** 审批驳回 */
    REJECTED,

    /** 已撤回 */
    WITHDRAWN,

    /** 签署中 */
    SIGNING,

    /** 已签署 */
    SIGNED,

    /** 已生效 */
    EFFECTIVE,

    /** 已归档 */
    ARCHIVED,

    /** 已终止 */
    TERMINATED,

    /** 已作废 */
    VOID;

    /**
     * 判断状态文本是否为枚举值之一。
     *
     * @param value 状态文本
     * @return 匹配的枚举；无匹配返回 {@code null}
     */
    public static ContractStatus of(String value) {
        if (value == null) {
            return null;
        }
        for (ContractStatus status : values()) {
            if (status.name().equals(value)) {
                return status;
            }
        }
        return null;
    }
}
