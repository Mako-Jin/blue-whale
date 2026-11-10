package com.bluewhale.oa.approval.domain.model;

/**
 * 审批业务副作用执行状态。
 *
 * @author BlueWhale
 */
public enum ApprovalEffectStatus {

    /** 待执行 */
    PENDING,

    /** 已完成 */
    DONE,

    /** 执行失败 */
    FAILED
}
