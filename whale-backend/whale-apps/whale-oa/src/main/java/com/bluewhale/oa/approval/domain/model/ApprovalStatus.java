package com.bluewhale.oa.approval.domain.model;

import com.bluewhale.oa.common.OaErrorCode;

/**
 * 审批实例状态。
 *
 * <p>状态机：{@code RUNNING → APPROVED / REJECTED / CANCELLED / BROKEN}，终态不可回流。</p>
 *
 * @author BlueWhale
 */
public enum ApprovalStatus {

    /** 审批中 */
    RUNNING,

    /** 已通过 */
    APPROVED,

    /** 已驳回 */
    REJECTED,

    /** 已撤销 */
    CANCELLED,

    /** 异常中断（Workflow 实例丢失或同步失败） */
    BROKEN;

    /**
     * 是否为终态。
     *
     * @return 非审批中返回 {@code true}
     */
    public boolean terminal() {
        return this != RUNNING;
    }

    /**
     * 解析状态文本。
     *
     * @param value 状态文本
     * @return 审批状态
     */
    public static ApprovalStatus of(String value) {
        if (value == null) {
            throw OaErrorCode.exception(OaErrorCode.APPROVAL_RESULT_INVALID);
        }
        try {
            return ApprovalStatus.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw OaErrorCode.exception(OaErrorCode.APPROVAL_RESULT_INVALID);
        }
    }
}
