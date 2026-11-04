package com.bluewhale.crm.commerce.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;

import java.util.Locale;

/**
 * 报价状态。
 *
 * @author BlueWhale
 */
public enum QuoteStatus {

    /** 草稿 */
    DRAFT,

    /** 待审批 */
    PENDING_APPROVAL,

    /** 已通过（只读） */
    APPROVED,

    /** 已驳回 */
    REJECTED;

    /**
     * 解析状态文本。
     *
     * @param value 状态文本
     * @return 报价状态
     */
    public static QuoteStatus from(String value) {
        if (value == null || value.isBlank()) {
            throw CrmErrorCode.exception(CrmErrorCode.QUOTE_STATE_CONFLICT);
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw CrmErrorCode.exception(CrmErrorCode.QUOTE_STATE_CONFLICT);
        }
    }
}
