package com.bluewhale.crm.customer.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;

/**
 * 客户公海状态。
 *
 * @author BlueWhale
 */
public enum PoolStatus {

    /** 私有（有负责人） */
    OWNED,

    /** 公海（无负责人，可领取） */
    IN_POOL;

    /**
     * 解析公海状态。
     *
     * @param code 状态码
     * @return 公海状态
     */
    public static PoolStatus from(String code) {
        if (code == null || code.isBlank()) {
            return OWNED;
        }
        try {
            return valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "公海状态非法：" + code);
        }
    }
}
