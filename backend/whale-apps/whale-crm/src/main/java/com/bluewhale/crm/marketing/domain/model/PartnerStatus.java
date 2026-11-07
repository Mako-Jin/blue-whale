package com.bluewhale.crm.marketing.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;

import java.util.Locale;

/**
 * 渠道伙伴状态。
 *
 * @author BlueWhale
 */
public enum PartnerStatus {

    /** 启用 */
    ENABLED,

    /** 停用 */
    DISABLED;

    /**
     * 解析伙伴状态。
     *
     * @param value 文本
     * @return 伙伴状态；空值返回 {@link #ENABLED}
     */
    public static PartnerStatus from(String value) {
        if (value == null || value.isBlank()) {
            return ENABLED;
        }
        for (PartnerStatus status : values()) {
            if (status.name().equals(value.trim().toUpperCase(Locale.ROOT))) {
                return status;
            }
        }
        throw CrmErrorCode.exception(CrmErrorCode.PARTNER_STATE_CONFLICT, "渠道伙伴状态非法：" + value);
    }
}
