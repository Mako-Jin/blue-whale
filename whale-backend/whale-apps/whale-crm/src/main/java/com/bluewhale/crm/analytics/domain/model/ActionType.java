package com.bluewhale.crm.analytics.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;

/**
 * 行动项关联对象类型。
 *
 * @author BlueWhale
 */
public enum ActionType {

    /** 客户 */
    CUSTOMER,

    /** 商机 */
    OPPORTUNITY,

    /** 销售目标 */
    TARGET,

    /** 合同台账 */
    CONTRACT,

    /** 应收 / 发票 */
    INVOICE,

    /** 服务工单 */
    TICKET;

    /**
     * 解析关联对象类型。
     *
     * @param code 类型编码
     * @return 类型枚举
     */
    public static ActionType from(String code) {
        if (code == null || code.isBlank()) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "行动项关联类型不能为空");
        }
        try {
            return valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "行动项关联类型非法：" + code);
        }
    }
}
