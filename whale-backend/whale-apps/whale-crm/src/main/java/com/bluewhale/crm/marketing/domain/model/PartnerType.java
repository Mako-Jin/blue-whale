package com.bluewhale.crm.marketing.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;

import java.util.Locale;

/**
 * 渠道伙伴类型。
 *
 * @author BlueWhale
 */
public enum PartnerType {

    /** 代理商 */
    AGENT,

    /** 经销商 */
    DEALER,

    /** 其他 */
    OTHER;

    /**
     * 解析伙伴类型。
     *
     * @param value 文本
     * @return 伙伴类型；空值返回 {@link #OTHER}
     */
    public static PartnerType from(String value) {
        if (value == null || value.isBlank()) {
            return OTHER;
        }
        for (PartnerType type : values()) {
            if (type.name().equals(value.trim().toUpperCase(Locale.ROOT))) {
                return type;
            }
        }
        throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "渠道伙伴类型非法：" + value);
    }
}
