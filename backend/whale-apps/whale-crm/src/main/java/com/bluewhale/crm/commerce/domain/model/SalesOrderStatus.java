package com.bluewhale.crm.commerce.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;

import java.util.Locale;

/**
 * 商务订单状态。
 *
 * <p>状态机：{@code DRAFT → PENDING_APPROVAL → APPROVED / REJECTED}；
 * 审批通过后 {@code APPROVED → PUSHING → PUSHED / PUSH_FAILED}，失败可重试。</p>
 *
 * @author BlueWhale
 */
public enum SalesOrderStatus {

    /** 草稿 */
    DRAFT,

    /** 待审批 */
    PENDING_APPROVAL,

    /** 审批通过 */
    APPROVED,

    /** 审批驳回 */
    REJECTED,

    /** 下发中 */
    PUSHING,

    /** 已下发 ERP */
    PUSHED,

    /** 下发失败（可重试） */
    PUSH_FAILED,

    /** 已取消 */
    CANCELLED;

    /**
     * 解析状态文本。
     *
     * @param value 状态文本
     * @return 订单状态
     */
    public static SalesOrderStatus from(String value) {
        if (value == null || value.isBlank()) {
            throw CrmErrorCode.exception(CrmErrorCode.ORDER_STATE_CONFLICT);
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw CrmErrorCode.exception(CrmErrorCode.ORDER_STATE_CONFLICT);
        }
    }
}
