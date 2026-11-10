package com.bluewhale.crm.commerce.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;

import java.util.Locale;

/**
 * 开票申请类型。
 *
 * @author BlueWhale
 */
public enum InvoiceRequestType {

    /** 正向开票申请 */
    NORMAL,

    /** 红冲申请 */
    RED_FLUSH;

    /**
     * 解析类型文本。
     *
     * @param value 类型文本
     * @return 申请类型
     */
    public static InvoiceRequestType from(String value) {
        if (value == null || value.isBlank()) {
            throw CrmErrorCode.exception(CrmErrorCode.INVOICE_STATE_CONFLICT);
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw CrmErrorCode.exception(CrmErrorCode.INVOICE_STATE_CONFLICT);
        }
    }
}
