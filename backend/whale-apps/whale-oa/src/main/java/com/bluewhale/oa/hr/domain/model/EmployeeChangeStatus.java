package com.bluewhale.oa.hr.domain.model;

/**
 * 人员异动单状态。
 *
 * @author BlueWhale
 */
public enum EmployeeChangeStatus {

    /** 草稿 */
    DRAFT,
    /** 审批中 */
    SUBMITTED,
    /** 审批通过（待执行） */
    APPROVED,
    /** 审批驳回 */
    REJECTED,
    /** 已执行 */
    EXECUTED,
    /** 执行失败（可重试） */
    EXEC_FAILED;

    /**
     * 是否可编辑。
     *
     * @return 可编辑返回 true
     */
    public boolean editable() {
        return this == DRAFT || this == REJECTED || this == EXEC_FAILED;
    }

    /**
     * 是否可提交审批。
     *
     * @return 可提交返回 true
     */
    public boolean submittable() {
        return this == DRAFT || this == REJECTED;
    }
}
