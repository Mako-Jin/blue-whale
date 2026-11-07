package com.bluewhale.crm.service.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;

/**
 * 工单状态。
 *
 * <p>主流程 {@code NEW → ASSIGNED → PROCESSING ⇄ WAITING_CUSTOMER → RESOLVED → CLOSED}，
 * 重开进入 {@code REOPENED} 后再回到处理；未关闭前允许取消。</p>
 *
 * @author BlueWhale
 */
public enum TicketStatus {

    /** 待分派 */
    NEW,

    /** 已分派 */
    ASSIGNED,

    /** 处理中 */
    PROCESSING,

    /** 等待客户回复 */
    WAITING_CUSTOMER,

    /** 已解决 */
    RESOLVED,

    /** 已关闭 */
    CLOSED,

    /** 已重开 */
    REOPENED,

    /** 已取消 */
    CANCELLED;

    /**
     * 解析状态。
     *
     * @param value 文本
     * @return 状态
     */
    public static TicketStatus from(String value) {
        if (value == null || value.isBlank()) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "工单状态不能为空");
        }
        for (TicketStatus status : values()) {
            if (status.name().equals(value.trim().toUpperCase(java.util.Locale.ROOT))) {
                return status;
            }
        }
        throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "工单状态非法：" + value);
    }
}
