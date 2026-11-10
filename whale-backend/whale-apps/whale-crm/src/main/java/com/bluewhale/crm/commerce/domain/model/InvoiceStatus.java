package com.bluewhale.crm.commerce.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;

import java.util.Locale;

/**
 * 开票申请状态。
 *
 * <p>状态机：{@code DRAFT → PENDING_APPROVAL → APPROVED / REJECTED}；
 * {@code APPROVED → ISSUED → SENT}；红冲单据直接落 {@code RED_FLUSHED}；
 * {@code DRAFT / REJECTED → CANCELLED}。</p>
 *
 * @author BlueWhale
 */
public enum InvoiceStatus {

    /** 草稿 */
    DRAFT,

    /** 待审批 */
    PENDING_APPROVAL,

    /** 审批通过 */
    APPROVED,

    /** 审批驳回 */
    REJECTED,

    /** 已开票 */
    ISSUED,

    /** 已寄送 */
    SENT,

    /** 已红冲（红冲单据终态） */
    RED_FLUSHED,

    /** 已取消 */
    CANCELLED;

    /**
     * 解析状态文本。
     *
     * @param value 状态文本
     * @return 开票状态
     */
    public static InvoiceStatus from(String value) {
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
