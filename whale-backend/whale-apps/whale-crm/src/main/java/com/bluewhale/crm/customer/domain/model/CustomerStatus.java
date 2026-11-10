package com.bluewhale.crm.customer.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;

/**
 * 客户状态。
 *
 * @author BlueWhale
 */
public enum CustomerStatus {

    /** 有效 */
    ACTIVE,

    /** 停用 */
    INACTIVE,

    /** 已合并（只读，指向目标客户） */
    MERGED;

    /**
     * 解析状态码。
     *
     * @param code 状态码
     * @return 状态枚举
     */
    public static CustomerStatus from(String code) {
        if (code == null || code.isBlank()) {
            throw CrmErrorCode.exception(CrmErrorCode.CUSTOMER_STATE_CONFLICT, "客户状态不能为空");
        }
        try {
            return valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw CrmErrorCode.exception(CrmErrorCode.CUSTOMER_STATE_CONFLICT, "客户状态非法：" + code);
        }
    }

    /**
     * 是否有效。
     *
     * @return 有效返回 true
     */
    public boolean active() {
        return this == ACTIVE;
    }
}
