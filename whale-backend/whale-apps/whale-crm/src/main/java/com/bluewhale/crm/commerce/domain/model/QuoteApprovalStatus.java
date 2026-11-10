package com.bluewhale.crm.commerce.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;

import java.util.Locale;

/**
 * 报价审批记录状态。
 *
 * @author BlueWhale
 */
public enum QuoteApprovalStatus {

    /** 待决策 */
    PENDING,

    /** 已通过 */
    APPROVED,

    /** 已驳回 / 撤销 */
    REJECTED;

    /**
     * 解析状态文本。
     *
     * @param value 状态文本
     * @return 审批状态
     */
    public static QuoteApprovalStatus from(String value) {
        if (value == null || value.isBlank()) {
            throw CrmErrorCode.exception(CrmErrorCode.QUOTE_APPROVAL_STATE_CONFLICT);
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw CrmErrorCode.exception(CrmErrorCode.QUOTE_APPROVAL_STATE_CONFLICT);
        }
    }
}
