package com.bluewhale.crm.marketing.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;

import java.util.Locale;

/**
 * 营销活动类型。
 *
 * @author BlueWhale
 */
public enum CampaignType {

    /** 展会 */
    EXHIBITION,

    /** 直播 */
    LIVE,

    /** 促销 */
    PROMOTION,

    /** 其他 */
    OTHER;

    /**
     * 解析活动类型。
     *
     * @param value 文本
     * @return 活动类型；空值返回 {@link #OTHER}
     */
    public static CampaignType from(String value) {
        if (value == null || value.isBlank()) {
            return OTHER;
        }
        for (CampaignType type : values()) {
            if (type.name().equals(value.trim().toUpperCase(Locale.ROOT))) {
                return type;
            }
        }
        throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "营销活动类型非法：" + value);
    }
}
