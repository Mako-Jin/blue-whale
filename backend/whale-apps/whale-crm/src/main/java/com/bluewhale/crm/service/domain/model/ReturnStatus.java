package com.bluewhale.crm.service.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;

import java.util.Locale;

/**
 * 退换货申请状态。
 *
 * <p>主链路：{@code DRAFT → PENDING_APPROVAL → APPROVED/REJECTED → EXECUTING → COMPLETED}；
 * {@code FAILED} 可重试，{@code DRAFT/REJECTED} 可取消。</p>
 *
 * @author BlueWhale
 */
public enum ReturnStatus {

    /** 草稿 */
    DRAFT,

    /** 审批中 */
    PENDING_APPROVAL,

    /** 审批通过 */
    APPROVED,

    /** 审批驳回 */
    REJECTED,

    /** 执行中（已下发 ERP） */
    EXECUTING,

    /** 已完成（ERP 退货过账成功） */
    COMPLETED,

    /** 执行失败，可重试 */
    FAILED,

    /** 已取消 */
    CANCELLED;

    /**
     * 解析状态文本。
     *
     * @param value 状态文本
     * @return 状态
     */
    public static ReturnStatus from(String value) {
        if (value == null || value.isBlank()) {
            throw CrmErrorCode.exception(CrmErrorCode.RETURN_STATE_CONFLICT, "退换货申请状态为空");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw CrmErrorCode.exception(CrmErrorCode.RETURN_STATE_CONFLICT, "未知退换货申请状态：" + value);
        }
    }
}
