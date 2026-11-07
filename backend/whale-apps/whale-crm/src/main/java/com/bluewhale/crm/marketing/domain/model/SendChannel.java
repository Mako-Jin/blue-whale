package com.bluewhale.crm.marketing.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;

import java.util.Locale;

/**
 * 触达渠道。
 *
 * @author BlueWhale
 */
public enum SendChannel {

    /** 邮件 */
    EMAIL,

    /** 短信 */
    SMS,

    /** 站内信 */
    INBOX;

    /**
     * 解析触达渠道。
     *
     * @param value 文本
     * @return 触达渠道
     */
    public static SendChannel from(String value) {
        if (value == null || value.isBlank()) {
            throw CrmErrorCode.exception(CrmErrorCode.MARKETING_SEND_INVALID, "触达渠道不能为空");
        }
        for (SendChannel channel : values()) {
            if (channel.name().equals(value.trim().toUpperCase(Locale.ROOT))) {
                return channel;
            }
        }
        throw CrmErrorCode.exception(CrmErrorCode.MARKETING_SEND_INVALID, "触达渠道非法：" + value);
    }
}
