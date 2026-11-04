package com.bluewhale.crm.sales.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;

/**
 * 线索状态：新建、已联系、已确认、已转化、无效。
 *
 * @author BlueWhale
 */
public enum LeadStatus {

    /** 新建 */
    NEW,

    /** 已联系 */
    CONTACTED,

    /** 已确认（可转化） */
    QUALIFIED,

    /** 已转化 */
    CONVERTED,

    /** 无效 */
    INVALID;

    /**
     * 解析状态码。
     *
     * @param code 状态码
     * @return 状态枚举
     */
    public static LeadStatus from(String code) {
        if (code == null || code.isBlank()) {
            throw CrmErrorCode.exception(CrmErrorCode.LEAD_STATE_CONFLICT);
        }
        String text = code.trim().toUpperCase();
        for (LeadStatus status : values()) {
            if (status.name().equals(text)) {
                return status;
            }
        }
        throw CrmErrorCode.exception(CrmErrorCode.LEAD_STATE_CONFLICT);
    }

    /**
     * 是否为终态（已转化 / 无效）。
     *
     * @return 终态返回 true
     */
    public boolean terminal() {
        return this == CONVERTED || this == INVALID;
    }

    /**
     * 判断是否允许流转到目标状态。
     *
     * @param target 目标状态
     * @return 允许返回 true
     */
    public boolean canMoveTo(LeadStatus target) {
        if (target == null || terminal() || target == NEW) {
            return false;
        }
        if (target == INVALID) {
            return true;
        }
        return switch (this) {
            case NEW -> target == CONTACTED;
            case CONTACTED -> target == QUALIFIED;
            case QUALIFIED -> target == CONVERTED || target == INVALID;
            default -> false;
        };
    }
}
