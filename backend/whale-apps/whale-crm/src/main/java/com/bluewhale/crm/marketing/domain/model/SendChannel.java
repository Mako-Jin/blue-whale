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

    /**
     * 解析消息服务的渠道编码（回执回调使用）。
     *
     * <p>消息服务把站内信拆成 {@code INAPP}（默认）与 {@code IM}，CRM 只有 {@code INBOX}，
     * 故回执侧必须做一次归一，否则「已送达」回执会被渠道校验直接拒掉。</p>
     *
     * @param value 消息服务渠道编码
     * @return 触达渠道
     */
    public static SendChannel fromMessageChannel(String value) {
        if (value != null) {
            String normalized = value.trim().toUpperCase(Locale.ROOT);
            if ("INAPP".equals(normalized) || "IM".equals(normalized)) {
                return INBOX;
            }
        }
        return from(value);
    }
}
