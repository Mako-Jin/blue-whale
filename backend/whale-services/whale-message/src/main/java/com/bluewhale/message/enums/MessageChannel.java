package com.bluewhale.message.enums;

import java.util.Locale;

/**
 * 消息渠道。
 *
 * @author BlueWhale
 */
public enum MessageChannel {

    /** 站内信：只落消息记录，由查询接口/Portal 拉取 */
    INAPP,

    /** 邮件 */
    EMAIL,

    /** 短信 */
    SMS,

    /** IM：经 whale-im 内网站内通知入口投递 */
    IM;

    /**
     * 解析渠道文本（大小写不敏感）。
     *
     * @param raw 原始文本
     * @return 渠道；无法识别时返回 {@code null}
     */
    public static MessageChannel parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
