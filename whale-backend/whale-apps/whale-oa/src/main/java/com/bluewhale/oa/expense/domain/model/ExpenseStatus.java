package com.bluewhale.oa.expense.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;

/**
 * 报销单状态。
 *
 * <p>状态机：{@code PENDING → APPROVED / REJECTED / CANCELLED}，终态不可回流。</p>
 *
 * @author BlueWhale
 */
public enum ExpenseStatus {

    /** 待审批 */
    PENDING,

    /** 已通过 */
    APPROVED,

    /** 已驳回 */
    REJECTED,

    /** 已撤销 */
    CANCELLED;

    /**
     * 解析状态文本。
     *
     * @param value 状态文本
     * @return 报销状态
     */
    public static ExpenseStatus of(String value) {
        try {
            return ExpenseStatus.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BizException(ErrorCode.PARAM_ERROR, "报销状态非法");
        }
    }
}
