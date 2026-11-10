package com.bluewhale.crm.analytics.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;

/**
 * 行动项状态。
 *
 * @author BlueWhale
 */
public enum ActionItemStatus {

    /** 待处理 */
    OPEN,

    /** 已关闭 */
    CLOSED;

    /**
     * 解析行动项状态。
     *
     * @param code 状态编码
     * @return 状态枚举
     */
    public static ActionItemStatus from(String code) {
        if (code == null || code.isBlank()) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "行动项状态不能为空");
        }
        try {
            return valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "行动项状态非法：" + code);
        }
    }
}
